package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import java.util.List;

/**
 * One type ready to be written to the index, with its rendered docs.
 *
 * @param type    the type with its chosen doc source
 * @param doc     the type's rendered doc ({@link RenderedDoc#missing()} if none, always for decompiled types)
 * @param members its API (public/protected) members with their docs, in declaration order
 */
public record TypeEntry(TypeDescriptor type, RenderedDoc doc, List<MemberEntry> members) {
    public TypeEntry {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        doc = doc == null ? RenderedDoc.missing() : doc;
        members = members == null ? List.of() : List.copyOf(members);
    }

    /** An entry without any docs (decompiled types). */
    public static TypeEntry undocumented(TypeDescriptor type, List<MemberDescriptor> members) {
        return new TypeEntry(type, RenderedDoc.missing(),
                members == null ? List.of() : members.stream().map(MemberEntry::undocumented).toList());
    }

    /** The member descriptors, in order. */
    public List<MemberDescriptor> memberDescriptors() {
        return members.stream().map(MemberEntry::descriptor).toList();
    }

    /** The member docs, aligned with {@link #memberDescriptors()}. */
    public List<RenderedDoc> memberDocs() {
        return members.stream().map(MemberEntry::doc).toList();
    }
}
