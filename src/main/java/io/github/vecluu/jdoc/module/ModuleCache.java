package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The per-module part of jdoc's cache: {@code modules/<key>/deps.txt} and {@code modules/<key>/meta.json}.
 * A {@code deps.txt} only ever appears after a successful Maven run.
 */
public interface ModuleCache {

    /** The resolved list for a POM key, if Maven succeeded for it before. */
    Optional<String> readDependencyList(String pomKey);

    /** A fresh, not yet existing file Maven may write the list for {@code pomKey} to. */
    Path newOutputFile(String pomKey);

    /** Publishes {@code outputFile} as the list for {@code meta.pomKey()} and writes the meta file. */
    void commit(Path outputFile, ModuleMeta meta);

    /** Removes an output file of a failed run, if it exists. */
    void discard(Path outputFile);

    /** The most recently resolved list for a module directory under any POM key. */
    Optional<CachedDependencyList> latestFor(Path moduleDirectory);
}
