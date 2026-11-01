package io.github.vecluu.jdoc.config;

import io.github.vecluu.jdoc.error.ConfigurationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Reads a JDK's {@code release} file for the feature version ({@code JAVA_VERSION="25.0.1"} → 25; legacy
 * {@code 1.8.0_x} → 8) and locates {@code lib/src.zip}. A missing {@code src.zip} is an empty optional.
 */
public final class JdkInfoReader {

    private static final String VERSION_KEY = "JAVA_VERSION=";

    /**
     * @throws ConfigurationException if the directory has no readable {@code release} file with a version
     */
    public JdkInfo read(Path javaHome) {
        Path release = javaHome.resolve("release");
        List<String> lines;
        try {
            lines = Files.readAllLines(release, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigurationException("not a JDK (cannot read " + release + "): " + e.getMessage(), e);
        }
        String version = lines.stream()
                .map(String::strip)
                .filter(line -> line.startsWith(VERSION_KEY))
                .map(line -> unquote(line.substring(VERSION_KEY.length())))
                .findFirst()
                .orElseThrow(() -> new ConfigurationException("no JAVA_VERSION in " + release));
        Path srcZip = javaHome.resolve("lib").resolve("src.zip");
        return new JdkInfo(javaHome, featureVersion(version, release),
                Files.isRegularFile(srcZip) ? Optional.of(srcZip) : Optional.empty());
    }

    static int featureVersion(String version, Path release) {
        String[] parts = version.split("[.+_-]");
        try {
            int first = Integer.parseInt(parts[0]);
            return first == 1 && parts.length > 1 ? Integer.parseInt(parts[1]) : first;
        } catch (NumberFormatException e) {
            throw new ConfigurationException("unparseable JAVA_VERSION '" + version + "' in " + release, e);
        }
    }

    private static String unquote(String value) {
        String stripped = value.strip();
        if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\"")) {
            return stripped.substring(1, stripped.length() - 1);
        }
        return stripped;
    }
}
