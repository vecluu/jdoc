package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs {@code mvn -o -q -B dependency:list} for one module. If the run in the module directory fails and a reactor
 * root exists above it, it retries once from the root with {@code -pl <module> -am}, so sibling modules that are not
 * installed in the local repository are built from the reactor.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MavenDependencyLister {

    private final MavenInvoker invoker;
    private final List<String> extraArguments;

    /** A lister running exactly the outline's command. */
    public MavenDependencyLister(MavenInvoker invoker) {
        this(invoker, List.of());
    }

    /**
     * A lister that appends arguments to every run, e.g. {@code -Dmaven.repo.local=<repo>} when the local
     * repository was configured explicitly.
     */
    public static MavenDependencyLister withExtraArguments(MavenInvoker invoker, List<String> extraArguments) {
        return new MavenDependencyLister(invoker, List.copyOf(extraArguments));
    }

    /** The arguments of the dependency listing, writing to {@code outputFile}. */
    public static List<String> arguments(Path outputFile) {
        return List.of("-o", "-q", "-B", "dependency:list",
                "-DincludeScope=test",
                "-DoutputAbsoluteArtifactFilename=true",
                "-DoutputFile=" + outputFile);
    }

    /**
     * Lists the module's dependencies into {@code outputFile}.
     *
     * @param moduleDirectory module to resolve
     * @param reactorRoot     root of the module's reactor, if known
     * @param outputFile      file Maven writes the list to
     * @return success only if Maven exited with 0 and wrote {@code outputFile}
     */
    public MavenResult list(Path moduleDirectory, Optional<Path> reactorRoot, Path outputFile) {
        MavenResult result = checkOutput(invoker.run(new MavenCommand(moduleDirectory, allArguments(outputFile))),
                outputFile);
        Optional<Path> root = reactorRoot
                .filter(candidate -> !candidate.equals(moduleDirectory) && moduleDirectory.startsWith(candidate));
        if (result.success() || root.isEmpty()) {
            return result;
        }
        log.debug("mvn failed in {} ({}), retrying from reactor root {}", moduleDirectory, result.summary(),
                root.get());
        deleteQuietly(outputFile);
        List<String> arguments = new ArrayList<>(allArguments(outputFile));
        arguments.addAll(List.of("-pl", root.get().relativize(moduleDirectory).toString(), "-am"));
        MavenResult retry = checkOutput(invoker.run(new MavenCommand(root.get(), arguments)), outputFile);
        return retry.success() ? retry : retry.output().isBlank() ? result : retry;
    }

    private List<String> allArguments(Path outputFile) {
        List<String> arguments = new ArrayList<>(arguments(outputFile));
        arguments.addAll(extraArguments);
        return arguments;
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
}
