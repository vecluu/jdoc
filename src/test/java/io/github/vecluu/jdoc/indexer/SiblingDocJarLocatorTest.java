package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.Gav;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiblingDocJarLocatorTest {

    @TempDir
    Path dir;

    private final SiblingDocJarLocator locator = new SiblingDocJarLocator();

    private Path touch(String name) throws IOException {
        return Files.writeString(dir.resolve(name), "x");
    }

    @Test
    void findsBothDocJars() throws IOException {
        Path jar = touch("a-1.0.jar");
        Path sources = touch("a-1.0-sources.jar");
        Path javadoc = touch("a-1.0-javadoc.jar");

        assertThat(locator.locate(Gav.of("g", "a", "1.0"), jar)).isEqualTo(
                new DocJars(Optional.of(sources), Optional.of(javadoc)));
    }

    @Test
    void missingDocJarsAreEmpty() throws IOException {
        Path jar = touch("a-1.0.jar");
        touch("a-1.0-javadoc.jar");
        touch("b-1.0-sources.jar");

        DocJars located = locator.locate(Gav.of("g", "a", "1.0"), jar);

        assertThat(located.sourcesJar()).isEmpty();
        assertThat(located.javadocJar()).isPresent();
    }

    @Test
    void usesTheJarFileNameForSnapshots() throws IOException {
        Path jar = touch("a-1.0-20240101.120000-3.jar");
        Path sources = touch("a-1.0-20240101.120000-3-sources.jar");
        touch("a-1.0-SNAPSHOT-sources.jar");

        assertThat(locator.locate(Gav.of("g", "a", "1.0-20240101.120000-3"), jar).sourcesJar()).contains(sources);
    }

    @Test
    void classifierJarsAreTheirOwnArtifacts() throws IOException {
        Path jar = touch("a-1.0-linux.jar");
        touch("a-1.0-sources.jar");
        Gav gav = new Gav("g", "a", "1.0", Optional.of("linux"), "jar");

        assertThat(locator.locate(gav, jar)).isEqualTo(DocJars.none());
        Path own = touch("a-1.0-linux-sources.jar");
        assertThat(locator.locate(gav, jar).sourcesJar()).contains(own);
    }

    @Test
    void directoriesDoNotCount() throws IOException {
        Path jar = touch("a-1.0.jar");
        Files.createDirectory(dir.resolve("a-1.0-sources.jar"));

        assertThat(locator.locate(Gav.of("g", "a", "1.0"), jar).sourcesJar()).isEmpty();
    }
}
