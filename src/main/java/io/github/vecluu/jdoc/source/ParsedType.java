package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.List;

/**
 * A type parsed from source with its members in declaration order (implicit members after the declared ones).
 *
 * @param descriptor the index descriptor
 * @param doc        the rendered class comment; {@link RenderedDoc#missing()} if there is none
 * @param members    all members, of every visibility; filter with {@code visibility().isApi()} for the API
 */
public record ParsedType(TypeDescriptor descriptor, RenderedDoc doc, List<ParsedMember> members) {
    public ParsedType {
        if (descriptor == null || doc == null) {
            throw new IllegalArgumentException("descriptor and doc are required");
        }
        members = members == null ? List.of() : List.copyOf(members);
    }

    /** The binary name of the type. */
    public TypeName name() {
        return descriptor.name();
    }

    /** Members with the given name (constructors are named like the type), in declaration order. */
    public List<ParsedMember> membersNamed(String name) {
        return members.stream().filter(member -> member.descriptor().name().equals(name)).toList();
    }
}
