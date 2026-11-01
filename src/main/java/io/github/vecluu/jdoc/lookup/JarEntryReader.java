package io.github.vecluu.jdoc.lookup;

import java.nio.file.Path;
import java.util.Optional;

/** Reads single text entries from jars and zips (sources jars, {@code src.zip}); a boundary for tests. */
public interface JarEntryReader {

    /**
     * The entry's text, decoded as UTF-8.
     *
     * @param jar       the jar or zip file
     * @param entryPath entry path inside it, e.g. {@code java.base/java/util/Optional.java}
     * @return the text, or empty if the jar has no such entry
     * @throws io.github.vecluu.jdoc.error.JarReadException if the jar cannot be read
     */
    Optional<String> readText(Path jar, String entryPath);
}
