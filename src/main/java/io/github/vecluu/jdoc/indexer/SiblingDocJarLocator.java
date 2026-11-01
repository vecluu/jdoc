package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Looks for {@code <stem>-sources.jar} and {@code <stem>-javadoc.jar} in the binary jar's directory, where the stem
 * is the binary jar's file name without {@code .jar} ({@code commons-lang3-3.17.0}). Using the actual file name
 * keeps timestamped and {@code -SNAPSHOT} file names consistent. A classifier jar is its own artifact: for
 * {@code x-1.0-linux.jar} only {@code x-1.0-linux-sources.jar} counts, never the main artifact's doc jars.
 */
public final class SiblingDocJarLocator implements DocJarLocator {

    private static final String JAR = ".jar";

    @Override
    public DocJars locate(Gav gav, Path binaryJar) {
        Path directory = binaryJar.toAbsolutePath().getParent();
        String fileName = binaryJar.getFileName().toString();
        if (directory == null || !fileName.endsWith(JAR)) {
            return DocJars.none();
        }
        String stem = fileName.substring(0, fileName.length() - JAR.length());
        return new DocJars(find(directory.resolve(stem + "-sources.jar")),
                find(directory.resolve(stem + "-javadoc.jar")));
    }

    private static Optional<Path> find(Path candidate) {
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }
}
