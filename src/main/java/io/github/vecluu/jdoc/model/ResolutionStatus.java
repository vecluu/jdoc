package io.github.vecluu.jdoc.model;

/** How trustworthy a module's resolved classpath is. */
public enum ResolutionStatus {
    /** Maven resolved the classpath (now or in a cached run for the current POM key). */
    RESOLVED,
    /** Maven failed; the last good list for the module was used. */
    STALE,
    /** No list was available; versions are guesses (newest in the local repository). */
    UNRESOLVED
}
