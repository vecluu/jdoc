package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs {@code mvn -o -q -B dependency:list} for one module.
 *
 * <p><b>Sibling modules.</b> Maven 3 cannot resolve a sibling module that is not installed in the local repository
 * when only {@code dependency:list} runs, not even with {@code -pl <module> -am} from the reactor root: the reactor
 * hands out a sibling's {@code target/classes} only if the lifecycle reaches {@code compile} ({@code test-compile}
 * for {@code test-jar} siblings). The reactor command therefore adds the {@code test-compile} phase with the compiler,
 * test compilation and resource copying skipped ({@link #REACTOR_ARGUMENTS}); plugins bound to earlier phases (code
 * generators, enforcer) still run. It is tried first when the module declares a sibling dependency, otherwise only
 * after the plain command failed.
 *
 * <p><b>Plugin prefix.</b> When offline Maven cannot resolve the {@code dependency:} prefix (no plugin group
 * metadata in the local repository), the run is repeated with the fully qualified goal from the fallback supplier,
 * which is then kept for the remaining attempts.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MavenDependencyLister {

    /** The goal of the outline's command. */
    public static final String PREFIX_GOAL = "dependency:list";

    /** Arguments added to the reactor command so siblings resolve to their (possibly absent) output directories. */
    public static final List<String> REACTOR_ARGUMENTS = List.of("-Dmaven.main.skip=true",
            "-Dmaven.test.skip=true", "-Dmaven.resources.skip=true");

    private static final String REACTOR_PHASE = "test-compile";
    private static final String MISSING_PREFIX = "No plugin found for prefix 'dependency'";

    private final MavenInvoker invoker;
    private final List<String> extraArguments;
    private final Supplier<Optional<String>> fallbackGoal;

    /** A lister running exactly the outline's command, without a fallback goal. */
    public MavenDependencyLister(MavenInvoker invoker) {
        this(invoker, List.of(), Optional::empty);
    }

    /**
     * A lister that appends arguments to every run, e.g. {@code -Dmaven.repo.local=<repo>} when the local
     * repository was configured explicitly.
     */
    public static MavenDependencyLister withExtraArguments(MavenInvoker invoker, List<String> extraArguments) {
        return new MavenDependencyLister(invoker, List.copyOf(extraArguments), Optional::empty);
    }

    /** A copy that retries with the supplied fully qualified goal when Maven cannot resolve the plugin prefix. */
    public MavenDependencyLister withFallbackGoal(Supplier<Optional<String>> goal) {
        return new MavenDependencyLister(invoker, extraArguments, goal);
    }

    /** The arguments of the dependency listing, writing to {@code outputFile}. */
    public static List<String> arguments(Path outputFile) {
        return arguments(PREFIX_GOAL, outputFile);
    }

    private static List<String> arguments(String goal, Path outputFile) {
        return List.of("-o", "-q", "-B", goal,
                "-DincludeScope=test",
                "-DoutputAbsoluteArtifactFilename=true",
                "-DoutputFile=" + outputFile);
    }

    /** Lists the module's dependencies, trying the module directory before the reactor root. */
    public MavenResult list(Path moduleDirectory, Optional<Path> reactorRoot, Path outputFile) {
        return list(moduleDirectory, reactorRoot, false, outputFile);
    }

    /**
     * Lists the module's dependencies into {@code outputFile}.
     *
     * @param moduleDirectory module to resolve
     * @param reactorRoot     root of the module's reactor, if known
     * @param reactorFirst    run the reactor command first (the module declares a dependency on a sibling module)
     * @param outputFile      file Maven writes the list to
     * @return success only if Maven exited with 0 and wrote {@code outputFile}; else the most telling failure
     */
    public MavenResult list(Path moduleDirectory, Optional<Path> reactorRoot, boolean reactorFirst,
                            Path outputFile) {
        Optional<Path> root = reactorRoot
                .filter(candidate -> !candidate.equals(moduleDirectory) && moduleDirectory.startsWith(candidate));
        List<Attempt> attempts = new ArrayList<>();
        Attempt module = new Attempt(moduleDirectory, Optional.empty());
        Optional<Attempt> reactor = root.map(path -> new Attempt(path,
                Optional.of(path.relativize(moduleDirectory).toString())));
        if (!reactorFirst || reactor.isEmpty()) {
            attempts.add(module);
        }
        reactor.ifPresent(attempts::add);

        String goal = PREFIX_GOAL;
        MavenResult first = null;
        MavenResult last = null;
        for (Attempt attempt : attempts) {
            last = run(attempt, goal, outputFile);
            if (!last.success() && goal.equals(PREFIX_GOAL) && last.output().contains(MISSING_PREFIX)) {
                Optional<String> qualified = fallbackGoal.get();
                if (qualified.isPresent()) {
                    goal = qualified.get();
                    log.debug("mvn cannot resolve the dependency plugin prefix, retrying with {}", goal);
                    last = run(attempt, goal, outputFile);
                }
            }
            if (last.success()) {
                return last;
            }
            log.debug("mvn failed in {}: {}", attempt.directory(), last.summary());
            first = first == null ? last : first;
        }
        return last.output().isBlank() ? first : last;
    }

    private MavenResult run(Attempt attempt, String goal, Path outputFile) {
        deleteQuietly(outputFile);
        List<String> arguments = new ArrayList<>(arguments(goal, outputFile));
        if (attempt.project().isPresent()) {
            arguments.add(arguments.indexOf(goal), REACTOR_PHASE);
            arguments.addAll(REACTOR_ARGUMENTS);
        }
        arguments.addAll(extraArguments);
        attempt.project().ifPresent(project -> arguments.addAll(List.of("-pl", project, "-am")));
        return checkOutput(invoker.run(new MavenCommand(attempt.directory(), arguments)), outputFile);
    }

    private static MavenResult checkOutput(MavenResult result, Path outputFile) {
        if (result.success() && !Files.isRegularFile(outputFile)) {
            return MavenResult.failed("mvn did not write " + outputFile + "\n" + result.output());
        }
        return result;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("cannot delete {}: {}", file, e.getMessage());
        }
    }

    /** One Maven run: in the module directory, or from the reactor root for {@code project} with {@code -am}. */
    private record Attempt(Path directory, Optional<String> project) {
    }
}
