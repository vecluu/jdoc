package io.github.vecluu.jdoc.lookup;

import java.util.Objects;

/**
 * A known subtype or implementation of a type (for {@code jdoc impls}).
 *
 * @param type   the subtype and its artifact
 * @param direct true if it names the queried type as a direct supertype
 */
public record Subtype(LocatedType type, boolean direct) {
    public Subtype {
        Objects.requireNonNull(type, "type");
    }
}
