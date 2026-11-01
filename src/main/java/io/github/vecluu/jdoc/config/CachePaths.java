package io.github.vecluu.jdoc.config;

import io.github.vecluu.jdoc.error.CacheIoException;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The layout of jdoc's cache directory:
 *
 * <pre>
 * &lt;cache&gt;/
 *   index.db                  SQLite index
 *   lock                      held while indexing
 *   modules/&lt;key&gt;/deps.txt    resolved classpath per module
 *   modules/&lt;key&gt;/meta.json   module meta
 *   module-snapshots/&lt;hash&gt;   POM analysis per module directory (key, reactor, file stamps)
 *   decompiled/&lt;g&gt;/&lt;a&gt;/&lt;v&gt;/   Vineflower output
 * </pre>
 *
 * <p>Path accessors are pure; directories are created only by the {@code create...} methods.
 */
public final class CachePaths {

    private static final Pattern MODULE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private final Path root;

    /**
     * @param root the cache directory; must be absolute
     */
    public CachePaths(Path root) {
        Objects.requireNonNull(root, "cache root must not be null");
        if (!root.isAbsolute()) {
            throw new IllegalArgumentException("cache root must be absolute: " + root);
        }
        this.root = root.normalize();
    }

    /** The cache directory itself. */
    public Path root() {
        return root;
    }

    /** The SQLite index file. */
    public Path indexDb() {
        return root.resolve("index.db");
    }

    /** The lock file held while indexing. */
    public Path lockFile() {
        return root.resolve("lock");
    }

    /** Parent of all per-module directories. */
    public Path modulesDirectory() {
        return root.resolve("modules");
    }

    /**
     * The directory for one module's POM key.
     *
     * @throws IllegalArgumentException if the key is not a safe path segment (letters, digits, {@code -}, {@code _})
     */
    public Path moduleDirectory(String pomKey) {
        if (pomKey == null || !MODULE_KEY.matcher(pomKey).matches()) {
            throw new IllegalArgumentException("invalid module key: " + pomKey);
        }
        return modulesDirectory().resolve(pomKey);
    }

    /** {@code modules/<key>/deps.txt}. */
    public Path depsFile(String pomKey) {
        return moduleDirectory(pomKey).resolve("deps.txt");
    }

    /** {@code modules/<key>/meta.json}. */
    public Path metaFile(String pomKey) {
        return moduleDirectory(pomKey).resolve("meta.json");
    }

    /** Directory of the per-module-directory POM analysis snapshots. */
    public Path moduleSnapshotsDirectory() {
        return root.resolve("module-snapshots");
    }

    /** Parent of all decompiled sources. */
    public Path decompiledRoot() {
        return root.resolve("decompiled");
    }

    /**
     * {@code decompiled/<groupPath>/<artifactId>/<version>/}; a classifier is appended to the version directory
     * ({@code <version>-<classifier>}) so classified jars do not share a cache.
     */
    public Path decompiledDirectory(Gav gav) {
        String versionDir = gav.version() + gav.classifier().map(c -> "-" + c).orElse("");
        return decompiledRoot().resolve(gav.groupPath()).resolve(gav.artifactId()).resolve(versionDir);
    }

    /** The cached decompiled source of a top-level type: {@code decompiled/.../<v>/a/b/Outer.java}. */
    public Path decompiledSource(Gav gav, TypeName type) {
        return decompiledDirectory(gav).resolve(type.topLevel().sourceEntryPath());
    }

    /** Creates the cache directory if needed and returns it. */
    public Path createRoot() {
        return createDirectories(root);
    }

    /** Creates {@code modules/<key>/} if needed and returns it. */
    public Path createModuleDirectory(String pomKey) {
        return createDirectories(moduleDirectory(pomKey));
    }

    /** Creates the decompiled directory of an artifact if needed and returns it. */
    public Path createDecompiledDirectory(Gav gav) {
        return createDirectories(decompiledDirectory(gav));
    }

    /**
     * Creates a directory (and parents) if needed.
     *
     * @throws CacheIoException if it cannot be created
     */
    public Path createDirectories(Path directory) {
        try {
            return Files.createDirectories(directory);
        } catch (IOException e) {
            throw new CacheIoException("cannot create cache directory " + directory + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "CachePaths[" + root + "]";
    }
}
