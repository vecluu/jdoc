package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyPluginLocatorTest {

    @TempDir
    Path repo;

    private final Path plugin = Path.of("org/apache/maven/plugins/maven-dependency-plugin");

    @Test
    void namesTheNewestVersionWithAJar() throws IOException {
        jar("3.7.0");
        jar("3.11.0");
        Files.createDirectories(repo.resolve(plugin).resolve("3.12.0"));

        assertThat(locator().qualifiedListGoal())
                .contains("org.apache.maven.plugins:maven-dependency-plugin:3.11.0:list");
    }

    @Test
    void emptyWithoutThePlugin() throws IOException {
        assertThat(locator().qualifiedListGoal()).isEmpty();
        Files.createDirectories(repo.resolve(plugin).resolve("3.7.0"));
        assertThat(locator().qualifiedListGoal()).isEmpty();
    }

    private DependencyPluginLocator locator() {
        return new DependencyPluginLocator(repo, new MavenVersionComparator());
    }

    private void jar(String version) throws IOException {
        Path directory = Files.createDirectories(repo.resolve(plugin).resolve(version));
        Files.writeString(directory.resolve("maven-dependency-plugin-" + version + ".jar"), "jar");
    }
}
