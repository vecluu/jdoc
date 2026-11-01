package io.github.vecluu.jdoc.query;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether a member's parameters match a parsed parameter list.
 *
 * <p>Arity must match, and each queried type must equal (case-sensitively) the member parameter's erased simple
 * type, or its declared type with type arguments erased, both normalized by {@link ParameterTypes}. So
 * {@code List} matches {@code List<String>}, {@code String[]} and {@code String...} match a varargs parameter,
 * {@code Entry} and {@code Map.Entry} both match a {@code Map.Entry} parameter, and a type variable parameter
 * {@code T} matches {@code T} as well as its erasure {@code Object}.
 */
public final class OverloadMatcher {

    /** True if the member matches; an absent parameter list (no parentheses) matches every overload. */
    public boolean matches(Optional<List<String>> queriedTypes, List<ParameterInfo> parameters) {
        return queriedTypes.map(types -> matches(types, parameters)).orElse(true);
    }

    /** True if the parameters match the queried normalized types exactly. */
    public boolean matches(List<String> queriedTypes, List<ParameterInfo> parameters) {
        if (queriedTypes.size() != parameters.size()) {
            return false;
        }
        for (int i = 0; i < parameters.size(); i++) {
            if (!matches(ParameterTypes.normalizeLenient(queriedTypes.get(i)), parameters.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True if the member is the one the reference names (the declaring type is not checked): same name,
     * constructor-ness as in the reference, and matching parameters. A reference with parentheses never matches a
     * field or enum constant.
     */
    public boolean matches(MemberReference reference, MemberDescriptor member) {
        if (!member.name().equals(reference.name())
                || reference.constructor() != (member.kind() == MemberKind.CONSTRUCTOR)) {
            return false;
        }
        if (reference.parameterTypes().isPresent() && !member.kind().isExecutable()) {
            return false;
        }
        return matches(reference.parameterTypes(), member.parameters());
    }

    private static boolean matches(String queried, ParameterInfo parameter) {
        return queried.equals(ParameterTypes.normalizeLenient(parameter.erasedType()))
                || queried.equals(ParameterTypes.normalizeLenient(parameter.type()));
    }
}
