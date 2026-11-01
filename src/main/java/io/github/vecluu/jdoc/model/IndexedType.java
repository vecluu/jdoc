package io.github.vecluu.jdoc.model;

/**
 * A row of the {@code types} table.
 *
 * @param id         database id
 * @param artifactId id of the owning {@link IndexedArtifact}
 * @param descriptor the type's data
 */
public record IndexedType(long id, long artifactId, TypeDescriptor descriptor) {
    public IndexedType {
        Checks.notNull(descriptor, "descriptor");
    }

    /** Shortcut for {@code descriptor().name()}. */
    public TypeName name() {
        return descriptor.name();
    }
}
