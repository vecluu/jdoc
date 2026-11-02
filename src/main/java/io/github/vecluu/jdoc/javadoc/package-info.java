/**
 * Javadoc to compact plain text ({@link io.github.vecluu.jdoc.model.RenderedDoc}), from doc comments
 * ({@link io.github.vecluu.jdoc.javadoc.DocCommentRenderer}, via {@code DocTrees}) and from javadoc-jar HTML pages
 * ({@link io.github.vecluu.jdoc.javadoc.JavadocJarReader}, via jsoup).
 *
 * <p>Both renderers share the same rules so their output is identical:
 * {@link io.github.vecluu.jdoc.javadoc.HtmlTextFormatter} (HTML fragment to text),
 * {@link io.github.vecluu.jdoc.javadoc.BlockTagFormatter} (one line per block tag) and
 * {@link io.github.vecluu.jdoc.javadoc.FirstSentenceExtractor}. A rendering rule changed in one place applies to both.
 * Like the rest of the indexing side, this package (and jsoup with it) stays off the lookup path.
 */
package io.github.vecluu.jdoc.javadoc;
