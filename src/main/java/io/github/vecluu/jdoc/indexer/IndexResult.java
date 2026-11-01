package io.github.vecluu.jdoc.indexer;

/**
 * The outcome of {@link ClasspathIndexer#index}.
 *
 * @param classpath the artifact ids to scope queries to
 * @param report    what was done
 */
public record IndexResult(IndexedClasspath classpath, IndexReport report) {
}
