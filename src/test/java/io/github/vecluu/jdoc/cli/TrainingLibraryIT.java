package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.cli.TrainingLibrary.Artifact;
import io.github.vecluu.jdoc.cli.TrainingLibrary.Flavour;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.module.DepsFileParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Generates the training library with the running JDK's compiler and javadoc tool. */
class TrainingLibraryIT {

    @TempDir
    Path tmp;

    @Test
    void writesThreeFlavoursIntoARepositoryLayout() throws IOException {
        Path repository = tmp.resolve("m2");

        List<Artifact> artifacts = TrainingLibrary.withSystemTools().orElseThrow().writeTo(repository);

        assertThat(artifacts).extracting(Artifact::flavour).containsExactly(Flavour.values());
        Artifact sources = artifacts.get(0);
        Artifact javadoc = artifacts.get(1);
        Artifact binary = artifacts.get(2);
        assertThat(sources.binaryJar()).isEqualTo(
                repository.resolve("jdoc/training/training-sources/1.0/training-sources-1.0.jar"));
        assertThat(sources.sourcesJar()).hasValueSatisfying(jar ->
                assertThat(jar.getFileName()).hasToString("training-sources-1.0-sources.jar"));
        assertThat(sources.javadocJar()).isEmpty();
        assertThat(javadoc.sourcesJar()).isEmpty();
        assertThat(javadoc.javadocJar()).hasValueSatisfying(jar ->
                assertThat(jar.getFileName()).hasToString("training-javadoc-1.0-javadoc.jar"));
        assertThat(binary.sourcesJar()).isEmpty();
        assertThat(binary.javadocJar()).isEmpty();
        assertThat(repository.resolve("jdoc/training/training-binary/1.0/training-binary-1.0.pom")).isRegularFile();

        assertThat(entries(binary.binaryJar())).contains("META-INF/MANIFEST.MF",
                "jdoc/training/binary/Widget.class", "jdoc/training/binary/Widget$Builder.class",
                "jdoc/training/binary/Mode$1.class").allSatisfy(name ->
                assertThat(name).matches("META-INF/MANIFEST\\.MF|jdoc/training/binary/[^/]+\\.class"));
        assertThat(entries(sources.sourcesJar().orElseThrow())).contains("jdoc/training/sources/Widget.java",
                "jdoc/training/sources/package-info.java").noneMatch(name -> name.endsWith(".class"));
        assertThat(entries(javadoc.javadocJar().orElseThrow())).contains("index.html",
                "jdoc/training/javadoc/Widget.html", "jdoc/training/javadoc/Widget.Builder.html",
                "jdoc/training/javadoc/package-summary.html");
        try (var leftovers = Files.list(repository)) {
            assertThat(leftovers).as("scratch directory removed").extracting(Path::getFileName)
                    .extracting(Path::toString).containsExactly("jdoc");
        }
    }

    @Test
    void mainWritesTheRepositoryAndTheDependencyList() throws IOException {
        Path list = tmp.resolve("library/deps.list");
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code = TrainingLibrary.run(new String[] {tmp.resolve("m2").toString(), list.toString()},
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(code).as(err.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(err.toString(StandardCharsets.UTF_8)).isEmpty();
        List<ResolvedDependency> parsed = new DepsFileParser().parse(Files.readString(list));
        assertThat(parsed).extracting(dependency -> dependency.gav().artifactId())
                .containsExactly("training-sources", "training-javadoc", "training-binary");
    }

    private static List<String> entries(Path jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return Collections.list(zip.entries()).stream().map(ZipEntry::getName).toList();
        }
    }
}
