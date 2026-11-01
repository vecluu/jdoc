package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.error.ResolutionException;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The last-resort classpath: for each declared dependency, the newest version directory in the local repository that
 * contains the artifact's jar. Versions are compared with a Maven-like ordering.
 */
@Slf4j
@RequiredArgsConstructor
public final class RepositoryVersionGuesser {

    private final Path localRepository;
    private final Comparator<String> versionOrder;

    /**
     * Guesses a classpath for the declared dependencies, keeping the first declaration of each artifact. Non-jar
     * types, system scope and dependencies with uninterpolated properties are skipped.
     */
    public List<ResolvedDependency> guess(List<DeclaredDependency> declared) {
        List<ResolvedDependency> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DeclaredDependency dependency : declared) {
            String identity = dependency.coordinates() + ":" + dependency.classifier().orElse("");
            if (!dependency.isJar() || dependency.hasUnresolvedProperty() || dependency.scope() == Scope.SYSTEM
                    || !seen.add(identity)) {
                continue;
            }
            newest(dependency).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private Optional<ResolvedDependency> newest(DeclaredDependency dependency) {
        Path artifactDirectory;
        try {
            artifactDirectory = localRepository.resolve(dependency.groupId().replace('.', '/'))
                    .resolve(dependency.artifactId());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!Files.isDirectory(artifactDirectory)) {
            log.debug("{} not found in the local repository", dependency.coordinates());
            return Optional.empty();
        }
        List<ResolvedDependency> candidates = new ArrayList<>();
        for (String version : versionDirectories(artifactDirectory)) {
            candidate(dependency, version, artifactDirectory.resolve(version)).ifPresent(candidates::add);
        }
        return candidates.stream()
                .max(Comparator.comparing((ResolvedDependency candidate) -> candidate.gav().version(), versionOrder));
    }

    private static Optional<ResolvedDependency> candidate(DeclaredDependency dependency, String version,
                                                          Path versionDirectory) {
        Gav gav;
        try {
            gav = new Gav(dependency.groupId(), dependency.artifactId(), version, dependency.classifier(),
                    dependency.type());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Path jar = versionDirectory.resolve(gav.fileName());
        return Files.isRegularFile(jar)
                ? Optional.of(new ResolvedDependency(gav, dependency.scope(), jar.toAbsolutePath()))
                : Optional.empty();
    }

    private static List<String> versionDirectories(Path artifactDirectory) {
        try (Stream<Path> children = Files.list(artifactDirectory)) {
            return children.filter(Files::isDirectory).map(path -> path.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new ResolutionException("cannot list " + artifactDirectory + ": " + e.getMessage(), e);
        }
    }
}
