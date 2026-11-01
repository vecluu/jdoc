package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;

/**
 * One member ready to be written to the index.
 *
 * @param descriptor the member
 * @param doc        its own rendered doc, before any {@code {@inheritDoc}} resolution (that happens at lookup time,
 *                   because supertypes may live in other artifacts); {@link RenderedDoc#missing()} if none
 */
public record MemberEntry(MemberDescriptor descriptor, RenderedDoc doc) {
    public MemberEntry {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor must not be null");
        }
        doc = doc == null ? RenderedDoc.missing() : doc;
    }

    /** A member without a doc. */
    public static MemberEntry undocumented(MemberDescriptor descriptor) {
        return new MemberEntry(descriptor, RenderedDoc.missing());
    }
}
