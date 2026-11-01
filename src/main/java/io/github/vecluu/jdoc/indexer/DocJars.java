package io.github.vecluu.jdoc.indexer;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The doc jars found next to a binary jar.
 *
 * @param sourcesJar the {@code -sources.jar}, if present
 * @param javadocJar the {@code -javadoc.jar}, if present
 */
public record DocJars(Optional<Path> sourcesJar, Optional<Path> javadocJar) {
    public DocJars {
        sourcesJar = sourcesJar == null ? Optional.empty() : sourcesJar;
        javadocJar = javadocJar == null ? Optional.empty() : javadocJar;
    }

    /** No doc jars. */
    public static DocJars none() {
        return new DocJars(Optional.empty(), Optional.empty());
    }
}
