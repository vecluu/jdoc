package io.github.vecluu.jdoc.lookup;

import java.util.List;
import java.util.Objects;

/**
 * Names of the members a type inherits from one supertype ({@code inherited from Object: getClass, notify}).
 *
 * @param declaringType the supertype declaring them
 * @param names         distinct member names in declaration order (overloads collapse into one name)
 */
public record InheritedMembers(LocatedType declaringType, List<String> names) {
    public InheritedMembers {
        Objects.requireNonNull(declaringType, "declaring type");
        names = List.copyOf(names);
    }
}
