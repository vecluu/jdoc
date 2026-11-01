package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import java.util.Objects;

/**
 * A member found for a query, with the type that declares it.
 *
 * @param member        the member row
 * @param declaringType the declaring type (for inherited members a supertype of the queried type)
 * @param inherited     true if the member was found on a supertype of the queried type
 */
public record MemberMatch(IndexedMember member, LocatedType declaringType, boolean inherited) {

    public MemberMatch {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(declaringType, "declaring type");
        if (member.typeId() != declaringType.id()) {
            throw new IllegalArgumentException("member " + member.descriptor().name() + " is not declared by "
                    + declaringType.name());
        }
    }

    /** The member's descriptor. */
    public MemberDescriptor descriptor() {
        return member.descriptor();
    }

    /**
     * {@code java.util.Map.Entry#getKey()}: declaring type display name, member name and erased simple parameter
     * types (no parentheses for fields and enum constants).
     */
    public String reference() {
        return member.descriptor().reference();
    }
}
