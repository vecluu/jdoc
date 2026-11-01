package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * The project a module belongs to.
 *
 * @param rootDirectory     directory of the topmost local POM (the reactor root); the module itself if standalone
 * @param moduleCoordinates {@code groupId:artifactId} of every POM in the project, the module included
 */
public record Reactor(Path rootDirectory, Set<String> moduleCoordinates) {

    public Reactor {
        Objects.requireNonNull(rootDirectory, "root directory must not be null");
        moduleCoordinates = Set.copyOf(moduleCoordinates);
    }

    /** True if the artifact is one of the project's own modules (any version). */
    public boolean contains(Gav gav) {
        return moduleCoordinates.contains(gav.coordinates());
    }
}
