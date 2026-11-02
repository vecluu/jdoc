package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.cli.TrainingLibrary.Artifact;
import io.github.vecluu.jdoc.cli.TrainingLibrary.Flavour;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import io.github.vecluu.jdoc.module.DepsFileParser;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrainingLibraryTest {

    @Test
    void flavoursHaveTheirOwnArtifactAndPackage() {
        assertThat(Flavour.SOURCES.artifactId()).isEqualTo("training-sources");
        assertThat(Flavour.JAVADOC.packageName()).isEqualTo("jdoc.training.javadoc");
        assertThat(Flavour.BINARY.packageName()).isEqualTo("jdoc.training.binary");
    }

    @Test
    void dependencyListIsWhatMavenWritesAndParsesBack() {
        Path root = Path.of("/repo dir/jdoc/training");
        List<Artifact> artifacts = List.of(
                new Artifact(Flavour.SOURCES, root.resolve("training-sources/1.0/training-sources-1.0.jar"),
                        Optional.of(root.resolve("training-sources/1.0/training-sources-1.0-sources.jar")),
                        Optional.empty()),
                new Artifact(Flavour.BINARY, root.resolve("training-binary/1.0/training-binary-1.0.jar"),
                        Optional.empty(), Optional.empty()));

        String list = TrainingLibrary.dependencyList(artifacts);
        List<ResolvedDependency> parsed = new DepsFileParser(path -> true).parse(list);

        assertThat(list).startsWith("\nThe following files have been resolved:\n").endsWith("\n");
        assertThat(parsed).extracting(dependency -> dependency.gav().toString())
                .containsExactly("jdoc.training:training-sources:1.0", "jdoc.training:training-binary:1.0");
        assertThat(parsed).extracting(ResolvedDependency::scope).containsOnly(Scope.COMPILE);
        assertThat(parsed.get(1).jar()).isEqualTo(artifacts.get(1).binaryJar());
    }

    @Test
    void wrongArgumentsPrintUsageAndExitWithTheErrorCode() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code = TrainingLibrary.run(new String[] {"only-one"}, new PrintStream(err, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(3);
        assertThat(err.toString(StandardCharsets.UTF_8)).startsWith("usage: TrainingLibrary <repository>");
    }
}
