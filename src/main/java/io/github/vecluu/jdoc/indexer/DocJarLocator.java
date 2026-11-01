package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Path;

/** Finds the sources and javadoc jars belonging to a binary jar; a filesystem boundary for tests. */
@FunctionalInterface
public interface DocJarLocator {

    /** The doc jars of the artifact {@code gav} whose binary jar is {@code binaryJar}. */
    DocJars locate(Gav gav, Path binaryJar);
}
