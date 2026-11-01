package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import java.util.List;

/**
 * One type ready to be written to the index.
 *
 * @param type    the type with its chosen doc source
 * @param members its API (public/protected) members
 */
public record TypeEntry(TypeDescriptor type, List<MemberDescriptor> members) {
    public TypeEntry {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        members = members == null ? List.of() : List.copyOf(members);
    }
}
