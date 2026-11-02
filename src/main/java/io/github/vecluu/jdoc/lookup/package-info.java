/**
 * Read-side services that answer every command's question from the index, scoped to the artifacts of one module's
 * classpath plus the JDK ({@link io.github.vecluu.jdoc.lookup.LookupScope}).
 *
 * <p>Results are structured values, never formatted text: found, not found and ambiguous are all values. Full
 * documentation is read from the index, where the indexer stored it rendered
 * ({@link io.github.vecluu.jdoc.lookup.DocLoader}); only source text is loaded on demand from the single jar entry
 * of a type, parsed or decompiled ({@link io.github.vecluu.jdoc.lookup.SourceLoader}, {@code jdoc source}).
 * {@link io.github.vecluu.jdoc.lookup.LookupServices} wires the whole package from the index repositories.
 *
 * <p>Package-wide rules:
 * <ul>
 *   <li>Services keep small per-process caches and are not thread-safe: use one set per call (per thread).</li>
 *   <li>The scope lists the JDK first, so a library declaring a JDK binary name never shadows the JDK's type (as in
 *       javac). The same binary name in several artifacts is not ambiguous: the first in classpath order wins.</li>
 *   <li>Doc inheritance is resolved here, at lookup time, because supertypes may live in other artifacts.</li>
 *   <li>Only {@code source} may load javac or Vineflower, and only through suppliers that do not name the concrete
 *       class ({@code HotPathClassLoadingIT}); every other service answers from SQLite alone.</li>
 * </ul>
 */
package io.github.vecluu.jdoc.lookup;
