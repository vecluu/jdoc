package io.github.vecluu.jdoc.bytecode;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Lists what a binary jar contains: every named type with its API members (see {@link ClassFileReader}).
 * Implementations touch the filesystem; tests may fake this.
 */
public interface BinaryJarScanner {

    /**
     * Streams every named class of the jar to {@code consumer}, one at a time, in entry order. Skips
     * {@code module-info}, {@code package-info}, everything under {@code META-INF/} (so base entries win over
     * {@code META-INF/versions/**}), synthetic, anonymous and local classes. Unreadable class entries are logged and
     * skipped.
     *
     * @throws io.github.vecluu.jdoc.error.JarReadException if the jar cannot be opened or read
     */
    void scan(Path jar, Consumer<ScannedClass> consumer);
}
