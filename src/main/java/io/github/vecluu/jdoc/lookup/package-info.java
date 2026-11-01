/**
 * Read-side services that answer every command's question from the index, scoped to the artifacts of one module's
 * classpath plus the JDK ({@link io.github.vecluu.jdoc.lookup.LookupScope}).
 *
 * <p>Results are structured values, never formatted text: found, not found and ambiguous are all values. Full
 * documentation and source text are loaded on demand from the single jar entry of a type
 * ({@link io.github.vecluu.jdoc.lookup.DocLoader}, {@link io.github.vecluu.jdoc.lookup.SourceLoader}).
 * {@link io.github.vecluu.jdoc.lookup.LookupServices} wires the whole package from the index repositories.
 *
 * <p>Services keep small per-process caches and are not thread-safe: use one set per call (per thread).
 */
package io.github.vecluu.jdoc.lookup;
