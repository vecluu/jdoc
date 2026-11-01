package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.Objects;
import java.util.Optional;

/**
 * The full documentation of a member.
 *
 * @param doc           the rendered doc, with {@code {@inheritDoc}} and missing parts filled in where possible;
 *                      {@link RenderedDoc#missing()} when there is none (e.g. decompiled types)
 * @param inheritedFrom the supertype the (first) inherited part came from, if any
 */
public record MemberDoc(RenderedDoc doc, Optional<TypeName> inheritedFrom) {
    public MemberDoc {
        Objects.requireNonNull(doc, "doc");
        inheritedFrom = inheritedFrom == null ? Optional.empty() : inheritedFrom;
    }
}
