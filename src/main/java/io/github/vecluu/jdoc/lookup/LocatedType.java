package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.Objects;

/**
 * An indexed type together with the artifact (in scope) it comes from.
 *
 * @param type     the type row
 * @param artifact the artifact of the type
 */
public record LocatedType(IndexedType type, ScopedArtifact artifact) {

    public LocatedType {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(artifact, "artifact");
        if (type.artifactId() != artifact.id()) {
            throw new IllegalArgumentException("type " + type.name() + " does not belong to artifact " + artifact);
        }
    }

    /** The binary name. */
    public TypeName name() {
        return type.name();
    }

    /** The type's descriptor. */
    public TypeDescriptor descriptor() {
        return type.descriptor();
    }

    /** Database id of the type. */
    public long id() {
        return type.id();
    }

    /** Label of the artifact, {@code jdk 25}. */
    public String label() {
        return artifact.label();
    }
}
