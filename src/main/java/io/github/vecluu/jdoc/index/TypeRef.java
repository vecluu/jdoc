package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.Objects;

/**
 * A lightweight type row: just enough for in-memory name matching (camel humps) over a whole scope.
 *
 * @param typeId     id of the type row
 * @param artifactId id of the owning artifact
 * @param name       the type's binary name
 */
public record TypeRef(long typeId, long artifactId, TypeName name) {
    public TypeRef {
        Objects.requireNonNull(name, "name");
    }
}
