package io.github.vecluu.jdoc.indexer;

import java.nio.file.Path;

/** Opens doc jars; a filesystem boundary for tests. */
public interface ArchiveOpener {

    /**
     * Opens a sources jar.
     *
     * @throws io.github.vecluu.jdoc.error.JarReadException if it cannot be opened
     */
    SourceArchive openSources(Path jar);

    /**
     * Opens a javadoc jar.
     *
     * @throws io.github.vecluu.jdoc.error.JarReadException if it cannot be opened
     */
    JavadocArchive openJavadoc(Path jar);
}
