package io.github.vecluu.jdoc.indexer;

import java.util.Optional;

/** Read access to the {@code .java} files of a sources jar (or {@code src.zip}); a boundary for tests. */
public interface SourceArchive extends AutoCloseable {

    /**
     * The UTF-8 text of an entry.
     *
     * @param entryPath e.g. {@code com/acme/Foo.java}
     * @return the text, or empty if there is no such file entry
     * @throws io.github.vecluu.jdoc.error.JarReadException if the archive cannot be read
     */
    Optional<String> read(String entryPath);

    @Override
    void close();
}
