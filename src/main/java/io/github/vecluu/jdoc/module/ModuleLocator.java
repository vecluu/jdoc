package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.error.ResolutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Finds the Maven module enclosing a file or directory: the nearest directory upwards with a {@code pom.xml}. */
public final class ModuleLocator {

    /** File name of a module POM. */
    public static final String POM_FILE = "pom.xml";

    private final Path workingDirectory;

    /** @param workingDirectory absolute directory that relative start paths are resolved against */
    public ModuleLocator(Path workingDirectory) {
        this.workingDirectory = Objects.requireNonNull(workingDirectory, "working directory must not be null");
    }

    /**
     * Returns the module directory (real path) for {@code start}, a file or directory that need not exist.
     *
     * @throws ResolutionException if no {@code pom.xml} exists in {@code start} or any ancestor
     */
    public Path locate(Path start) {
        Path absolute = workingDirectory.resolve(start).toAbsolutePath().normalize();
        Path directory = Files.isDirectory(absolute) ? absolute : absolute.getParent();
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve(POM_FILE))) {
                return realPath(current);
            }
        }
        throw new ResolutionException("no pom.xml found in " + absolute + " or any parent directory");
    }

    private static Path realPath(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException e) {
            return directory;
        }
    }
}
