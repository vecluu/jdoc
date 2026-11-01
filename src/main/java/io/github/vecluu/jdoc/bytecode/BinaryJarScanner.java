package io.github.vecluu.jdoc.bytecode;

import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Lists what a binary jar contains: every named type with its API members (see {@link ClassFileReader}), plus the
 * packages that have a {@code package-info.class}. Implementations touch the filesystem; tests may fake this.
 */
public interface BinaryJarScanner {

    /**
     * Streams every named class of the jar to {@code consumer}, one at a time, in entry order. Skips
     * {@code module-info}, everything under {@code META-INF/} (so base entries win over
     * {@code META-INF/versions/**}), synthetic, anonymous and local classes. Unreadable class entries are logged and
     * skipped.
     *
     * @throws io.github.vecluu.jdoc.error.JarReadException if the jar cannot be opened or read
     */
    JarScanSummary scan(Path jar, Consumer<ScannedClass> consumer);

    /**
     * Reads one type from the jar.
     *
     * @return the type, or empty if the jar has no such class entry or it is not a named type
     * @throws io.github.vecluu.jdoc.error.JarReadException if the jar cannot be read
     */
    Optional<ScannedClass> readClass(Path jar, TypeName type);

    /** Convenience: all classes of the jar in a list (fine for tests and small jars). */
    default List<ScannedClass> scanAll(Path jar) {
        List<ScannedClass> result = new ArrayList<>();
        scan(jar, result::add);
        return result;
    }
}
