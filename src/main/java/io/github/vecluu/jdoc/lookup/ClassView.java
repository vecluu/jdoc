package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.IndexedMember;
import java.util.List;
import java.util.Objects;

/**
 * Everything {@code jdoc class} shows.
 *
 * @param type             the type and its artifact (header line)
 * @param directSupertypes direct supertypes as declared (superclass first), known or not
 * @param firstParagraph   first paragraph of the class comment; empty if there is none (always for decompiled)
 * @param members          own public and protected members: enum constants, fields, constructors, then methods
 *                         (javadoc's summary order), each group in declaration order
 * @param nestedTypes      API-visible member types declared directly in this type, by name
 * @param inherited        inherited member names grouped by declaring supertype, in javadoc hierarchy order
 */
public record ClassView(
        LocatedType type,
        List<Supertype> directSupertypes,
        String firstParagraph,
        List<IndexedMember> members,
        List<LocatedType> nestedTypes,
        List<InheritedMembers> inherited) {

    public ClassView {
        Objects.requireNonNull(type, "type");
        directSupertypes = List.copyOf(directSupertypes);
        firstParagraph = firstParagraph == null ? "" : firstParagraph;
        members = List.copyOf(members);
        nestedTypes = List.copyOf(nestedTypes);
        inherited = List.copyOf(inherited);
    }
}
