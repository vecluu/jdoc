package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.Gav;

/** Drops cached decompiled sources of an artifact whose jar is re-indexed; a filesystem boundary for tests. */
@FunctionalInterface
public interface DecompiledCacheEvictor {

    /** Removes everything cached for the artifact; failures are logged, never thrown. */
    void evict(Gav gav);
}
