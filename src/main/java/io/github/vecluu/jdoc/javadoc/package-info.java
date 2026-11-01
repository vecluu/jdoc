/**
 * Javadoc to compact plain text ({@link io.github.vecluu.jdoc.model.RenderedDoc}).
 *
 * <p>Shared rules (owned by T01) that both renderers must use so their output is identical:
 * {@link io.github.vecluu.jdoc.javadoc.HtmlTextFormatter} (HTML fragment to text),
 * {@link io.github.vecluu.jdoc.javadoc.BlockTagFormatter} (one line per block tag) and
 * {@link io.github.vecluu.jdoc.javadoc.FirstSentenceExtractor}. Doc-comment rendering via {@code DocTrees} (T04)
 * and javadoc-jar HTML pages (T05) build on them.
 */
package io.github.vecluu.jdoc.javadoc;
