package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Small helpers that write POM files for module tests. */
final class PomFixtures {

    private PomFixtures() {
    }

    /** Writes {@code <dir>/pom.xml} with the given body inside a namespaced {@code <project>}. */
    static Path pom(Path directory, String... body) {
        return writeFile(directory.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                %s
                </project>
                """.formatted(String.join("\n", body)));
    }

    static Path writeFile(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    static String gav(String groupId, String artifactId, String version) {
        return "<groupId>%s</groupId><artifactId>%s</artifactId><version>%s</version>"
                .formatted(groupId, artifactId, version);
    }

    static String artifact(String artifactId) {
        return "<artifactId>" + artifactId + "</artifactId>";
    }

    static String parent(String groupId, String artifactId, String version) {
        return "<parent>" + gav(groupId, artifactId, version) + "</parent>";
    }

    static String parent(String groupId, String artifactId, String version, String relativePathElement) {
        return "<parent>" + gav(groupId, artifactId, version) + relativePathElement + "</parent>";
    }

    static String modules(String... names) {
        return Arrays.stream(names).map(name -> "<module>" + name + "</module>")
                .collect(Collectors.joining("", "<modules>", "</modules>"));
    }

    static String dependencies(String... dependencies) {
        return "<dependencies>" + String.join("", dependencies) + "</dependencies>";
    }

    static String dependency(String groupId, String artifactId) {
        return "<dependency><groupId>%s</groupId><artifactId>%s</artifactId></dependency>"
                .formatted(groupId, artifactId);
    }

    static String dependency(String groupId, String artifactId, String extra) {
        return "<dependency><groupId>%s</groupId><artifactId>%s</artifactId>%s</dependency>"
                .formatted(groupId, artifactId, extra);
    }

    static Path touch(Path file) {
        return writeFile(file, "");
    }
}
