package io.github.vecluu.jdoc.module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/** {@link PomFiles} on the default file system. */
public final class NioPomFiles implements PomFiles {

    @Override
    public boolean isRegularFile(Path path) {
        return Files.isRegularFile(path);
    }

    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    @Override
    public byte[] read(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    @Override
    public FileTime lastModifiedTime(Path file) throws IOException {
        return Files.getLastModifiedTime(file);
    }
}
