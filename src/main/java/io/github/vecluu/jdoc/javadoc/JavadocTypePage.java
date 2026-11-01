package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.List;
import java.util.Optional;

/**
 * A parsed javadoc type page: the type's doc and its member detail sections.
 *
 * @param type    the documented type
 * @param typeDoc the class description ({@link RenderedDoc#missing()} if the page has none)
 * @param members member detail sections in page order
 */
public record JavadocTypePage(TypeName type, RenderedDoc typeDoc, List<JavadocMemberDoc> members) {

    public JavadocTypePage {
        if (type == null || typeDoc == null) {
            throw new IllegalArgumentException("type and type doc must not be null");
        }
        members = members == null ? List.of() : List.copyOf(members);
    }

    /**
     * The doc of a member: the section whose anchor matches exactly (name and parameter types), else the only
     * section with the same name and arity. Empty if neither exists (e.g. an undocumented or inherited member, or
     * an ambiguous overload on an old-layout page).
     */
    public Optional<RenderedDoc> memberDoc(MemberDescriptor member) {
        for (JavadocMemberDoc section : members) {
            if (section.anchors().stream().anyMatch(anchor -> anchor.matches(member))) {
                return Optional.of(section.doc());
            }
        }
        List<JavadocMemberDoc> loose = members.stream()
                .filter(section -> section.anchors().stream().anyMatch(anchor -> anchor.matchesLoosely(member)))
                .toList();
        return loose.size() == 1 ? Optional.of(loose.getFirst().doc()) : Optional.empty();
    }
}
