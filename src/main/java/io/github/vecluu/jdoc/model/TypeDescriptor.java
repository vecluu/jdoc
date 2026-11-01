package io.github.vecluu.jdoc.model;

import java.util.List;
import java.util.Optional;
import lombok.Builder;

/**
 * Everything the index stores about a type.
 *
 * @param name             binary name
 * @param kind             class, interface, enum, record or annotation
 * @param visibility       effective declared visibility
 * @param modifiers        modifier text as shown to users, e.g. {@code public final}; may be empty
 * @param typeParameters   type parameter text, e.g. {@code <K, V>}; empty if not generic
 * @param supertypes       direct supertypes, superclass first, then interfaces in declaration order
 * @param deprecated       {@code @Deprecated} or {@code @deprecated}
 * @param firstSentence    first sentence of the doc comment, plain text; may be empty
 * @param docSource        where docs come from for this type
 * @param entryPath        entry inside the doc-source container: {@code a/b/Outer.java} (sources/JDK),
 *                         {@code a/b/Outer.Inner.html} (javadoc) or {@code a/b/Outer$Inner.class} (decompiled)
 * @param declarationRange source range of the declaration, when parsed from source
 */
@Builder(toBuilder = true)
public record TypeDescriptor(
        TypeName name,
        TypeKind kind,
        Visibility visibility,
        String modifiers,
        String typeParameters,
        List<TypeName> supertypes,
        boolean deprecated,
        String firstSentence,
        DocSource docSource,
        String entryPath,
        Optional<SourceRange> declarationRange) {

    public TypeDescriptor {
        Checks.notNull(name, "name");
        Checks.notNull(kind, "kind");
        Checks.notNull(visibility, "visibility");
        Checks.notNull(docSource, "doc source");
        Checks.notBlank(entryPath, "entry path");
        modifiers = modifiers == null ? "" : modifiers;
        typeParameters = typeParameters == null ? "" : typeParameters;
        supertypes = supertypes == null ? List.of() : List.copyOf(supertypes);
        firstSentence = firstSentence == null ? "" : firstSentence;
        declarationRange = declarationRange == null ? Optional.empty() : declarationRange;
    }
}
