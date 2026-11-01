package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * The file-system access of POM analysis (the POM key and the reactor): every file it reads and every path whose
 * existence or kind it tests goes through this interface, so that {@link RecordingPomFiles} can list the inputs of an
 * analysis and {@link SnapshotPomAnalyzer} can tell later whether any of them changed.
 */
public interface PomFiles {

    /** True if {@code path} is a regular file (following links). */
    boolean isRegularFile(Path path);

    /** True if {@code path} is a directory (following links). */
    boolean isDirectory(Path path);

    /** The file's bytes. */
    byte[] read(Path file) throws IOException;

    /** The file's last-modified time. */
    FileTime lastModifiedTime(Path file) throws IOException;
}
