package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.List;
import java.util.Optional;

/**
 * The documentation of one member detail section of a javadoc page.
 *
 * @param anchors all member anchors of the section (modern pages have the declared and the erased form for
 *                generic members, e.g. {@code max(T,T)} and {@code max(java.lang.Comparable,java.lang.Comparable)})
 * @param doc        the rendered doc
 * @param parameters the parameters of the section's member signature, with their real names; empty if the section
 *                   has no signature that could be read (fields and enum constants never have one)
 */
public record JavadocMemberDoc(List<JavadocAnchor> anchors, RenderedDoc doc,
                               Optional<List<JavadocParameter>> parameters) {

    /** A section without known parameters. */
    public JavadocMemberDoc(List<JavadocAnchor> anchors, RenderedDoc doc) {
        this(anchors, doc, Optional.empty());
    }

    public JavadocMemberDoc {
        if (anchors == null || anchors.isEmpty()) {
            throw new IllegalArgumentException("a member section needs at least one anchor");
        }
        anchors = List.copyOf(anchors);
        if (doc == null) {
            throw new IllegalArgumentException("doc must not be null");
        }
        parameters = parameters == null ? Optional.empty() : parameters.map(List::copyOf);
    }

    /** The member name of the section's first anchor ({@code <init>} for modern constructor anchors). */
    public String name() {
        return anchors.getFirst().name();
    }
}
