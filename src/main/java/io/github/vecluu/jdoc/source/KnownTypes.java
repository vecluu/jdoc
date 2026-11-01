package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.Collection;
import java.util.Set;

/**
 * Optional knowledge of which types exist, used to resolve simple names in source without attribution. A caller
 * that indexes a whole sources jar can pass the jar's types so same-package and on-demand-import names resolve
 * exactly; without it, resolution falls back to documented guesses.
 */
@FunctionalInterface
public interface KnownTypes {

    /** True if a type with this binary name is known to exist. */
    boolean contains(TypeName name);

    /** Knows nothing: every lookup is false. */
    static KnownTypes none() {
        return name -> false;
    }

    /** Knows exactly the given types. */
    static KnownTypes of(Collection<TypeName> names) {
        Set<TypeName> copy = Set.copyOf(names);
        return copy::contains;
    }
}
