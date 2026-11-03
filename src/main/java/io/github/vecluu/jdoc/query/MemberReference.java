package io.github.vecluu.jdoc.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A parsed {@code <type>#<name>[(<params>)]} argument.
 *
 * @param input          the argument as typed (trimmed)
 * @param type           the type part
 * @param name           the member name; for constructors the type's simple name (never {@code <init>})
 * @param constructor    true if the member is a constructor: {@code Type#<init>}, or {@code Type#Type} once the
 *                       lookup has found no own method matching it ({@link #asConstructor()})
 * @param parameterTypes normalized parameter types (see {@link ParameterTypes}); empty when no parentheses were
 *                       given, meaning "all overloads"; an empty list means "zero parameters"
 */
public record MemberReference(
        String input,
        TypeReference type,
        String name,
        boolean constructor,
        Optional<List<String>> parameterTypes) {

    public MemberReference {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        parameterTypes = parameterTypes == null ? Optional.empty() : parameterTypes.map(List::copyOf);
    }

    /** True if no parameter list was given, so every overload is meant. */
    public boolean allOverloads() {
        return parameterTypes.isEmpty();
    }

    /**
     * True for {@code Type#Type}: a method reference whose name is the type's simple name. It means the type's
     * own methods that match it (by name, and by parameters when given), its constructors when none does.
     */
    public boolean namedLikeType() {
        return !constructor && name.equals(type.simpleName());
    }

    /** This reference with the same name and parameters, naming constructors. */
    public MemberReference asConstructor() {
        return new MemberReference(input, type, name, true, parameterTypes);
    }
}
