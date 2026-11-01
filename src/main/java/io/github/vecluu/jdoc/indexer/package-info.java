/**
 * Lazy and forced indexing: makes sure every jar of a module's classpath, plus the JDK, is present and current in
 * the index ({@link io.github.vecluu.jdoc.indexer.ClasspathIndexer}), choosing the best doc source per type
 * ({@link io.github.vecluu.jdoc.indexer.JarArtifactIndexer}: sources jar, then javadoc jar, then bytecode only)
 * and indexing the JDK's {@code src.zip} ({@link io.github.vecluu.jdoc.indexer.DefaultJdkIndexer}).
 */
package io.github.vecluu.jdoc.indexer;
