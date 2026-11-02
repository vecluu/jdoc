package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds the newest {@code maven-dependency-plugin} whose jar is in the local repository and names its {@code list}
 * goal with full coordinates. Offline Maven resolves the {@code dependency:} prefix through the plugin group's
 * {@code maven-metadata-<repository id>.xml}; a pre-filled airgap repository often lacks that file (or has it under a
 * different repository id) while the plugin itself is present, so the fully qualified goal still works.
 */
@Slf4j
@RequiredArgsConstructor
public final class DependencyPluginLocator {

    /** {@code groupId:artifactId} of the plugin. */
    public static final String PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin";

    private static final Path PLUGIN_DIRECTORY = Path.of("org", "apache", "maven", "plugins",
            "maven-dependency-plugin");

    private final Path localRepository;
    private final Comparator<String> versionOrder;

    /** {@code org.apache.maven.plugins:maven-dependency-plugin:<newest local version>:list}, if any version is there. */
    public Optional<String> qualifiedListGoal() {
        Path pluginDirectory = localRepository.resolve(PLUGIN_DIRECTORY);
        if (!Files.isDirectory(pluginDirectory)) {
            return Optional.empty();
        }
        try (Stream<Path> versions = Files.list(pluginDirectory)) {
            return versions
                    .filter(version -> Files.isRegularFile(version.resolve(
                            "maven-dependency-plugin-" + version.getFileName() + ".jar")))
                    .map(version -> version.getFileName().toString())
                    .max(versionOrder)
                    .map(version -> PLUGIN + ":" + version + ":list");
        } catch (IOException e) {
            log.debug("cannot list {}: {}", pluginDirectory, e.getMessage());
            return Optional.empty();
        }
    }
}
