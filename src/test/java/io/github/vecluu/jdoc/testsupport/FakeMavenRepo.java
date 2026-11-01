package io.github.vecluu.jdoc.testsupport;

import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A local Maven repository in a temp directory, laid out as {@code <root>/g/r/o/u/p/artifactId/version/}.
 *
 * <pre>{@code
 * FakeMavenRepo repo = new FakeMavenRepo(env.localRepository());
 * ResolvedDependency dep = repo.install(SampleLibrary.complete(), Scope.COMPILE);
 * Files.writeString(depsTxt, FakeMavenRepo.depsTxt(List.of(dep)));
 * }</pre>
 */
public final class FakeMavenRepo {

    private final Path root;

    /** @param root the repository root (e.g. {@code JdocEnvironment.localRepository()}); created on demand */
    public FakeMavenRepo(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** The repository root. */
    public Path root() {
        return root;
    }

    /** Absolute version directory of an artifact. */
    public Path artifactDirectory(Gav gav) {
        return root.resolve(gav.repositoryDirectory());
    }

    /** Builds the library's jars into its version directory and writes a minimal POM next to them. */
    public BuiltLibrary installLibrary(TestLibrary library) {
        BuiltLibrary built = library.writeTo(artifactDirectory(library.gav()));
        writePom(library.gav());
        return built;
    }

    /** Installs the library and returns it as a dependency with the given scope. */
    public ResolvedDependency install(TestLibrary library, Scope scope) {
        BuiltLibrary built = installLibrary(library);
        return new ResolvedDependency(library.gav(), scope, built.binaryJar());
    }

    /** Installs the library as a {@link Scope#COMPILE} dependency. */
    public ResolvedDependency install(TestLibrary library) {
        return install(library, Scope.COMPILE);
    }

    /** Writes a minimal POM for the coordinates (no dependencies) and returns its path. */
    public Path writePom(Gav gav) {
        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(gav.groupId(), gav.artifactId(), gav.baseVersion());
        Path file = root.resolve(gav.pomPath());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, pom, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    /**
     * One line as {@code mvn dependency:list -DoutputAbsoluteArtifactFilename=true} writes it:
     * {@code "   g:a:type[:classifier]:v:scope:/abs/path.jar"}.
     */
    public static String depsTxtLine(ResolvedDependency dependency) {
        Gav gav = dependency.gav();
        return "   " + gav.groupId() + ":" + gav.artifactId() + ":" + gav.type()
                + gav.classifier().map(c -> ":" + c).orElse("")
                + ":" + gav.version() + ":" + dependency.scope().mavenName() + ":" + dependency.jar();
    }

    /** A complete {@code deps.txt} including Maven's header line, ending with a newline. */
    public static String depsTxt(List<ResolvedDependency> dependencies) {
        return dependencies.stream()
                .map(FakeMavenRepo::depsTxtLine)
                .collect(Collectors.joining("\n", "\nThe following files have been resolved:\n", "\n"));
    }
}
