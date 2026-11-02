/**
 * Lazy and forced indexing, the write side of jdoc: makes sure every jar of a module's classpath, plus the JDK, is
 * present and current in the index, with fully rendered docs.
 *
 * <p>Entry points:
 * <ul>
 *   <li>{@link io.github.vecluu.jdoc.indexer.ClasspathIndexer}: stats the jars, takes the indexing lock only when
 *       something is stale, and returns the artifact ids every lookup is scoped to;</li>
 *   <li>{@link io.github.vecluu.jdoc.indexer.JarArtifactIndexer}: one Maven jar, its types written in batches;</li>
 *   <li>{@code TypeDocResolver}: the per-type doc-source fallback, sources jar, then javadoc jar, then bytecode only
 *       (the order is specified in {@code docs/SPEC.md}, "Doc sources"). Supertypes of jar types always come from
 *       bytecode, even for types indexed from sources;</li>
 *   <li>{@link io.github.vecluu.jdoc.indexer.DefaultJdkIndexer}: the JDK from {@code src.zip}, or from {@code jrt:/}
 *       (bytecode only) without it.</li>
 * </ul>
 *
 * <p>Package-wide rules:
 * <ul>
 *   <li>The only package that writes to the index, from the calling thread only; parallel parsing feeds that one
 *       writer.</li>
 *   <li>Types of every visibility are stored (package-private and private nested ones too); members only if they
 *       are API.</li>
 *   <li>A type's member docs are aligned by position with its members; keep the two lists parallel. A missing doc is
 *       never stored (no row means no doc).</li>
 *   <li>The JDK's artifact key is {@code jdk:<feature>} and its stamp is that of {@code src.zip}, so switching between
 *       two JDKs of the same feature version re-indexes the JDK each time. {@code force} re-indexes Maven artifacts
 *       only; the JDK needs {@code forceJdk}.</li>
 *   <li>Indexing components load javac, jsoup and ASM, so {@code cli.CompositionRoot} creates them lazily.</li>
 * </ul>
 */
package io.github.vecluu.jdoc.indexer;
