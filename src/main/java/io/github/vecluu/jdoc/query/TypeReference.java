package io.github.vecluu.jdoc.query;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.List;
import java.util.Objects;

/**
 * A parsed {@code <type>} argument and its candidate interpretations, most likely first.
 *
 * @param input      the argument as typed (trimmed)
 * @param names      the name pieces as typed, split at {@code .} and nesting {@code $}, type arguments removed
 * @param candidates interpretations to try in order; never empty
 */
public record TypeReference(String input, List<String> names, List<TypeCandidate> candidates) {

    public TypeReference {
        Objects.requireNonNull(input, "input");
        names = List.copyOf(names);
        candidates = List.copyOf(candidates);
        if (names.isEmpty() || candidates.isEmpty()) {
            throw new IllegalArgumentException("a type reference needs names and candidates");
        }
    }

    /** The last name piece, i.e. the simple name of the type meant, {@code Entry} for {@code Map.Entry}. */
    public String simpleName() {
        return names.getLast();
    }

    /** True if any candidate accepts the given type. */
    public boolean matches(TypeName type) {
        return candidates.stream().anyMatch(candidate -> candidate.matches(type));
    }
}
