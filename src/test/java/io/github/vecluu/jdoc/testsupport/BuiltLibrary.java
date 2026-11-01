package io.github.vecluu.jdoc.testsupport;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The jars written by {@link TestLibrary#writeTo(Path)}.
 *
 * @param gav        coordinates the jars are named after
 * @param binaryJar  {@code <a>-<v>.jar} with the compiled classes
 * @param sourcesJar {@code <a>-<v>-sources.jar}, if requested
 * @param javadocJar {@code <a>-<v>-javadoc.jar} with real JDK javadoc HTML, if requested
 */
public record BuiltLibrary(Gav gav, Path binaryJar, Optional<Path> sourcesJar, Optional<Path> javadocJar) {
}
