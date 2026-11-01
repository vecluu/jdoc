package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The parts of a POM jdoc needs.
 *
 * @param file         absolute, normalized path of the POM file
 * @param coordinates  own coordinates, with groupId and version inherited from the parent when absent; empty if
 *                     they cannot be determined
 * @param parent       the {@code <parent>} element, if any
 * @param modules      entries of {@code <modules>} / {@code <subprojects>} (including those inside profiles)
 * @param dependencies directly declared dependencies
 */
public record PomModel(Path file, Optional<Gav> coordinates, Optional<ParentReference> parent,
                       List<String> modules, List<DeclaredDependency> dependencies) {

    public PomModel {
        Objects.requireNonNull(file, "file must not be null");
        file = file.toAbsolutePath().normalize();
        coordinates = coordinates == null ? Optional.empty() : coordinates;
        parent = parent == null ? Optional.empty() : parent;
        modules = modules == null ? List.of() : List.copyOf(modules);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }

    /** The directory containing the POM. */
    public Path directory() {
        return file.getParent();
    }

    /** True if this POM's coordinates are exactly {@code gav} (group, artifact and version). */
    public boolean hasCoordinates(Gav gav) {
        return coordinates.map(own -> own.toString().equals(gav.toString())).orElse(false);
    }
}
