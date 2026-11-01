package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.pom;
import static io.github.vecluu.jdoc.module.PomFixtures.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.ResolutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleLocatorTest {

    @TempDir
    Path tmp;

    @Test
    void findsModuleFromNestedSourceFile() throws IOException {
        Path module = tmp.resolve("app");
        pom(module, PomFixtures.gav("g", "app", "1"));
        Path source = touch(module.resolve("src/main/java/com/example/App.java"));

        assertThat(new ModuleLocator(tmp).locate(source)).isEqualTo(module.toRealPath());
    }

    @Test
    void directoryWithPomIsItsOwnModule() throws IOException {
        pom(tmp.resolve("parent"), PomFixtures.gav("g", "p", "1"));
        Path child = tmp.resolve("parent/child");
        pom(child, PomFixtures.gav("g", "c", "1"));

        assertThat(new ModuleLocator(tmp).locate(child)).isEqualTo(child.toRealPath());
    }

    @Test
    void relativeStartIsResolvedAgainstWorkingDirectory() throws IOException {
        pom(tmp, PomFixtures.gav("g", "root", "1"));
        Files.createDirectories(tmp.resolve("src"));

        assertThat(new ModuleLocator(tmp).locate(Path.of("src"))).isEqualTo(tmp.toRealPath());
        assertThat(new ModuleLocator(tmp).locate(Path.of("."))).isEqualTo(tmp.toRealPath());
    }

    @Test
    void nonExistingFileStillWalksUp() throws IOException {
        pom(tmp, PomFixtures.gav("g", "root", "1"));

        assertThat(new ModuleLocator(tmp).locate(tmp.resolve("does/not/exist/Foo.java")))
                .isEqualTo(tmp.toRealPath());
    }

    @Test
    void failsWithoutPom() {
        Path dir = tmp.resolve("nothing/here");

        assertThatThrownBy(() -> new ModuleLocator(tmp).locate(dir))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("no pom.xml found");
    }

    @Test
    void ignoresDirectoryNamedPomXml() throws IOException {
        pom(tmp, PomFixtures.gav("g", "root", "1"));
        Path sub = tmp.resolve("sub");
        Files.createDirectories(sub.resolve("pom.xml"));

        assertThat(new ModuleLocator(tmp).locate(sub)).isEqualTo(tmp.toRealPath());
    }
}
