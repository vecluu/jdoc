package io.github.vecluu.jdoc.source;

import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where a type is written: the innermost enclosing type (for name resolution) and the type variables in scope with
 * their first bound (for erasure). Immutable.
 */
record TypeScope(Optional<TypeName> enclosingType, Map<String, Optional<Tree>> typeVariables) {

    TypeScope {
        typeVariables = Map.copyOf(typeVariables);
    }

    /** The scope at file level: no enclosing type, no type variables. */
    static TypeScope fileLevel() {
        return new TypeScope(Optional.empty(), Map.of());
    }

    /** This scope inside the body of the given type. */
    TypeScope enter(TypeName type) {
        return new TypeScope(Optional.of(type), typeVariables);
    }

    /** This scope plus the given type parameters, which shadow outer ones of the same name. */
    TypeScope withTypeParameters(List<? extends TypeParameterTree> parameters) {
        if (parameters.isEmpty()) {
            return this;
        }
        Map<String, Optional<Tree>> variables = new HashMap<>(typeVariables);
        for (TypeParameterTree parameter : parameters) {
            Optional<Tree> bound = parameter.getBounds().isEmpty()
                    ? Optional.empty()
                    : Optional.of(parameter.getBounds().getFirst());
            variables.put(parameter.getName().toString(), bound);
        }
        return new TypeScope(enclosingType, variables);
    }

    boolean isTypeVariable(String name) {
        return typeVariables.containsKey(name);
    }
}
