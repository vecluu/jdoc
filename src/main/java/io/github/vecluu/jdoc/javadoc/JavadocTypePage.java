package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.ParameterInfo;
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

    /**
     * The real parameter names of an executable member, from the signature of the section whose anchor matches it
     * exactly. Only when that signature has the member's arity and every shown parameter type agrees with the
     * member's (erased or declared) type, compared without type arguments and packages; never from a loose
     * (name and arity) match. Empty for fields, members without parameters and on any mismatch.
     */
    public Optional<List<String>> parameterNames(MemberDescriptor member) {
        if (!member.kind().isExecutable() || member.arity() == 0) {
            return Optional.empty();
        }
        return members.stream()
                .filter(section -> section.anchors().stream().anyMatch(anchor -> anchor.matches(member)))
                .findFirst()
                .flatMap(JavadocMemberDoc::parameters)
                .filter(shown -> sameTypes(shown, member.parameters()))
                .map(shown -> shown.stream().map(JavadocParameter::name).toList());
    }

    private static boolean sameTypes(List<JavadocParameter> shown, List<ParameterInfo> parameters) {
        if (shown.size() != parameters.size()) {
            return false;
        }
        for (int i = 0; i < shown.size(); i++) {
            String type = JavadocAnchor.simpleForm(shown.get(i).type());
            ParameterInfo parameter = parameters.get(i);
            if (!type.equals(JavadocAnchor.simpleForm(parameter.type()))
                    && !type.equals(JavadocAnchor.simpleForm(parameter.erasedType()))) {
                return false;
            }
        }
        return true;
    }
}
