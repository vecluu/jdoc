package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.Objects;
import java.util.Optional;

/**
 * One supertype in a hierarchy walk.
 *
 * @param name the supertype's binary name
 * @param type the indexed type, or empty if it is not in scope (kept by name only)
 */
public record Supertype(TypeName name, Optional<LocatedType> type) {

    public Supertype {
        Objects.requireNonNull(name, "name");
        type = type == null ? Optional.empty() : type;
    }

    /** True if the supertype is indexed in scope. */
    public boolean isKnown() {
        return type.isPresent();
    }
}
