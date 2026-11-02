package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.error.ResolutionException;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import java.nio.file.Path;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * The {@link ClasspathResolver} backed by Maven:
 *
 * <p>The POM key and the reactor come from a {@link SnapshotPomAnalyzer}: POMs are parsed only when one of them
 * (or a path the analysis looked at) changed since the last call for the module.
 *
 * <ol>
 *   <li>cache hit ({@code modules/<key>/deps.txt} exists) → {@link ResolutionStatus#RESOLVED}, no Maven run;</li>
 *   <li>miss → run {@code mvn dependency:list} (from the reactor root first if the module declares a sibling
 *       dependency, see {@link MavenDependencyLister}); on success store {@code deps.txt} and {@code meta.json};</li>
 *   <li>Maven failed → the most recent good list of the same module, {@link ResolutionStatus#STALE}, one warning;</li>
 *   <li>no list at all → newest jars in the local repository for the declared dependencies,
 *       {@link ResolutionStatus#UNRESOLVED}, one warning;</li>
 *   <li>nothing usable → {@link ResolutionException}.</li>
 * </ol>
 *
 * <p>The project's own reactor modules are always removed from the result.
 */
@Slf4j
@Builder
public final class MavenClasspathResolver implements ClasspathResolver {

    private static final DateTimeFormatter WARNING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @NonNull
    private final ModuleLocator locator;
    @NonNull
    private final PomHierarchy hierarchy;
    @NonNull
    private final PomAnalyzer analyzer;
    @NonNull
    private final MavenDependencyLister lister;
    @NonNull
    private final ModuleCache cache;
    @NonNull
    private final DepsFileParser parser;
    @NonNull
    private final RepositoryVersionGuesser guesser;
    @NonNull
    private final Clock clock;

    /** Wires the standard components for an environment, running Maven as a real process. */
    public static MavenClasspathResolver create(JdocEnvironment environment) {
        return create(environment, ProcessMavenInvoker.from(environment), Clock.systemDefaultZone());
    }

    /** Wires the standard components for an environment with the given Maven invoker and clock. */
    public static MavenClasspathResolver create(JdocEnvironment environment, MavenInvoker invoker, Clock clock) {
        RecordingPomFiles files = new RecordingPomFiles(new NioPomFiles());
        PomHierarchy hierarchy = new PomHierarchy(new PomReader(), files);
        PomAnalyzer parsing = new ParsingPomAnalyzer(new PomKeyCalculator(hierarchy, environment.localRepository()),
                new ReactorDetector(hierarchy));
        return builder()
                .locator(new ModuleLocator(environment.workingDirectory()))
                .hierarchy(hierarchy)
                .analyzer(new SnapshotPomAnalyzer(parsing, files,
                        new FileModuleSnapshotStore(environment.cachePaths(), new ModuleSnapshotCodec()),
                        new NioPathStamper(), environment.localRepository(), clock))
                .lister(MavenDependencyLister.withExtraArguments(invoker, environment.localRepositoryExplicit()
                                ? List.of("-Dmaven.repo.local=" + environment.localRepository())
                                : List.of())
                        .withFallbackGoal(new DependencyPluginLocator(environment.localRepository(),
                                new MavenVersionComparator())::qualifiedListGoal))
                .cache(new FileModuleCache(environment.cachePaths(), new ModuleMetaCodec()))
                .parser(new DepsFileParser())
                .guesser(new RepositoryVersionGuesser(environment.localRepository(), new MavenVersionComparator()))
                .clock(clock)
                .build();
    }

    @Override
    public ModuleClasspath resolve(Path start) {
        Path moduleDirectory = locator.locate(start);
        Path modulePom = moduleDirectory.resolve(ModuleLocator.POM_FILE);
        PomAnalysis analysis = analyzer.analyze(moduleDirectory);
        PomKey key = analysis.key();
        Reactor reactor = analysis.reactor();

        Optional<String> cached = cache.readDependencyList(key.value());
        if (cached.isPresent()) {
            log.debug("classpath cache hit for {} ({})", moduleDirectory, key.value());
            return classpath(moduleDirectory, key, cached.get(), reactor, ResolutionStatus.RESOLVED, List.of());
        }

        Path output = cache.newOutputFile(key.value());
        MavenResult result = lister.list(moduleDirectory, Optional.of(reactor.rootDirectory()),
                dependsOnSibling(modulePom, reactor), output);
        if (result.success()) {
            cache.commit(output, new ModuleMeta(moduleDirectory, key.value(), key.fingerprints(), clock.instant()));
            String content = cache.readDependencyList(key.value())
                    .orElseThrow(() -> new ResolutionException("resolved classpath vanished from the cache"));
            return classpath(moduleDirectory, key, content, reactor, ResolutionStatus.RESOLVED, List.of());
        }
        cache.discard(output);
        log.debug("mvn failed for {}: {}", moduleDirectory, result.summary());
        return fallback(moduleDirectory, modulePom, key, reactor, result);
    }

    private boolean dependsOnSibling(Path modulePom, Reactor reactor) {
        return reactor.moduleCoordinates().size() > 1 && declaredDependencies(modulePom).stream()
                .anyMatch(dependency -> reactor.moduleCoordinates().contains(dependency.coordinates()));
    }

    private List<DeclaredDependency> declaredDependencies(Path modulePom) {
        return hierarchy.localChain(modulePom).poms().stream()
                .flatMap(pom -> pom.dependencies().stream())
                .toList();
    }

    private ModuleClasspath fallback(Path moduleDirectory, Path modulePom, PomKey key, Reactor reactor,
                                     MavenResult result) {
        Optional<CachedDependencyList> lastGood = cache.latestFor(moduleDirectory);
        if (lastGood.isPresent()) {
            String time = WARNING_TIME.withZone(clock.getZone()).format(lastGood.get().meta().resolvedAt());
            return classpath(moduleDirectory, key, lastGood.get().content(), reactor, ResolutionStatus.STALE,
                    List.of("warning: mvn failed, using classpath resolved at " + time));
        }
        List<DeclaredDependency> declared = declaredDependencies(modulePom).stream()
                .filter(dependency -> !reactor.moduleCoordinates().contains(dependency.coordinates()))
                .toList();
        List<ResolvedDependency> guessed = guesser.guess(declared).stream()
                .filter(dependency -> !reactor.contains(dependency.gav()))
                .toList();
        boolean declaresJars = declared.stream().anyMatch(DeclaredDependency::isJar);
        if (guessed.isEmpty() && declaresJars) {
            throw new ResolutionException("mvn failed for " + moduleDirectory + " (" + result.summary()
                    + ") and no dependency was found in the local repository");
        }
        return new ModuleClasspath(moduleDirectory, key.value(), guessed, ResolutionStatus.UNRESOLVED,
                List.of("warning: mvn failed, versions unresolved: using newest versions in the local repository"));
    }

    private ModuleClasspath classpath(Path moduleDirectory, PomKey key, String depsText, Reactor reactor,
                                      ResolutionStatus status, List<String> warnings) {
        List<ResolvedDependency> dependencies = parser.parse(depsText).stream()
                .filter(dependency -> !reactor.contains(dependency.gav()))
                .toList();
        return new ModuleClasspath(moduleDirectory, key.value(), dependencies, status, warnings);
    }
}
