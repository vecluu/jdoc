/**
 * Decompiling with Vineflower, in-process, for {@code jdoc source} on types that have no source text: the one piece
 * of the indexing side used at lookup time. {@link io.github.vecluu.jdoc.decompile.Decompiler}
 * ({@link io.github.vecluu.jdoc.decompile.VineflowerDecompiler}) decompiles one top-level type of a jar;
 * {@link io.github.vecluu.jdoc.decompile.DecompiledSourceProvider}
 * ({@link io.github.vecluu.jdoc.decompile.DecompiledSourceCache}) keeps the result under {@code decompiled/} in the
 * cache, which is all the {@code lookup} package sees. Never used for JDK types.
 *
 * <p>Package-wide rules:
 * <ul>
 *   <li>The cache is not checked against the jar: it is evicted only when an already indexed Maven artifact is
 *       re-indexed ({@code indexer.DecompiledDirectoryEvictor}), before indexing, even if indexing then fails.</li>
 *   <li>Vineflower is loaded lazily ({@code cli.CompositionRoot}) and must stay off the lookup path of every other
 *       command ({@code HotPathClassLoadingIT}).</li>
 *   <li>Vineflower's plugins (Kotlin, idea-not-null) ship in the shaded jar and change its output. The decompile tests
 *       run on the unshaded classpath, so they do not see this.</li>
 *   <li>Failures are {@link io.github.vecluu.jdoc.decompile.DecompilationException}, the one {@code JdocException}
 *       subclass declared outside the {@code error} package.</li>
 * </ul>
 */
package io.github.vecluu.jdoc.decompile;
