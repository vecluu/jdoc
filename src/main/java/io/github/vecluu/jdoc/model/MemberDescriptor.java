package io.github.vecluu.jdoc.model;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;

/**
 * Everything the index stores about one member.
 *
 * @param declaringType binary name of the declaring type
 * @param name          member name; constructors use the declaring type's simple name (never {@code <init>})
 * @param kind          method, constructor, field or enum constant
 * @param signature     rendered one-line signature (see the architecture's signature format)
 * @param parameters    parameters in order; empty for fields and enum constants
 * @param visibility    effective visibility
 * @param isStatic      static member (enum constants count as static)
 * @param deprecated    {@code @Deprecated} or {@code @deprecated}
 * @param firstSentence first sentence of the doc comment, plain text; may be empty
 * @param range         source range from doc comment start through end of body, when parsed from source
 * @param docRange      source range of the doc comment alone, when parsed from source and documented
 */
@Builder(toBuilder = true)
public record MemberDescriptor(
        TypeName declaringType,
        String name,
        MemberKind kind,
        String signature,
        List<ParameterInfo> parameters,
        Visibility visibility,
        boolean isStatic,
        boolean deprecated,
        String firstSentence,
        Optional<SourceRange> range,
        Optional<SourceRange> docRange) {

    public MemberDescriptor {
        Checks.notNull(declaringType, "declaring type");
        Checks.notBlank(name, "name");
        Checks.notNull(kind, "kind");
        Checks.notBlank(signature, "signature");
        Checks.notNull(visibility, "visibility");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        firstSentence = firstSentence == null ? "" : firstSentence;
        range = range == null ? Optional.empty() : range;
        docRange = docRange == null ? Optional.empty() : docRange;
        if (kind == MemberKind.CONSTRUCTOR && !name.equals(declaringType.simpleName())) {
            throw new IllegalArgumentException(
                    "constructor name must be the simple type name '" + declaringType.simpleName() + "': " + name);
        }
        if (!kind.isExecutable() && !parameters.isEmpty()) {
            throw new IllegalArgumentException(kind + " cannot have parameters: " + name);
        }
    }

    /** Number of parameters. */
    public int arity() {
        return parameters.size();
    }

    /** Erased simple parameter types, in order: {@code [List, String[]]}. */
    public List<String> erasedParameterTypes() {
        return parameters.stream().map(ParameterInfo::erasedType).toList();
    }

    /**
     * Overload identity within a type: {@code name(Erased,Types)} for methods and constructors, {@code name} for
     * fields and enum constants. Two members with the same key override or hide each other.
     */
    public String erasedKey() {
        if (!kind.isExecutable()) {
            return name;
        }
        return erasedParameterTypes().stream().collect(Collectors.joining(",", name + "(", ")"));
    }

    /** {@code java.util.Map.Entry#getKey()}: display name of the declaring type plus {@link #erasedKey()}. */
    public String reference() {
        return declaringType.displayName() + "#" + erasedKey();
    }
}
