package io.github.vecluu.jdoc.indexer;

/**
 * How {@link ClasspathIndexer#index} works.
 *
 * @param force                re-index every artifact of the module, even if its jar is unchanged
 * @param forceJdk             re-index the JDK, even if unchanged
 * @param reportMissingSources fill {@link IndexReport#missingSources()} (one query per artifact; for
 *                             {@code jdoc index})
 */
public record IndexOptions(boolean force, boolean forceJdk, boolean reportMissingSources) {

    /** Lazy indexing for a lookup command: only new or changed jars, no missing-sources report. */
    public static IndexOptions lookup() {
        return new IndexOptions(false, false, false);
    }

    /** {@code jdoc index [--force]}: with the missing-sources report. */
    public static IndexOptions indexCommand(boolean force) {
        return new IndexOptions(force, false, true);
    }
}
