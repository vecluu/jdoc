package io.github.vecluu.jdoc.indexer;

import java.nio.file.Path;
import java.util.Optional;

/** Reads {@link FileStamp}s; a filesystem boundary for tests. */
@FunctionalInterface
public interface FileStatter {

    /** The file's stamp, or empty if it does not exist or is not a regular file. */
    Optional<FileStamp> stat(Path file);
}
