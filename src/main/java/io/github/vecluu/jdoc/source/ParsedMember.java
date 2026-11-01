package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;

/**
 * A member parsed from source: its descriptor and its fully rendered doc comment.
 *
 * @param descriptor the index descriptor (signature, parameters, offsets, first sentence)
 * @param doc        the rendered doc comment; {@link RenderedDoc#missing()} if there is none
 * @param implicit   true for members the compiler generates (default constructor, enum {@code values}/{@code valueOf},
 *                   record accessors and canonical constructor); they have no source range
 */
public record ParsedMember(MemberDescriptor descriptor, RenderedDoc doc, boolean implicit) {
    public ParsedMember {
        if (descriptor == null || doc == null) {
            throw new IllegalArgumentException("descriptor and doc are required");
        }
    }
}
