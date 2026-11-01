package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/** Builds the data of {@code jdoc class} for a resolved type. */
@RequiredArgsConstructor
public final class ClassViewService {

    private static final List<MemberKind> GROUP_ORDER =
            List.of(MemberKind.ENUM_CONSTANT, MemberKind.FIELD, MemberKind.CONSTRUCTOR, MemberKind.METHOD);

    private final ScopedTypes types;
    private final TypeHierarchy hierarchy;
    private final TypeMembers typeMembers;
    private final DocLoader docs;

    /** The class view of a type. */
    public ClassView view(LocatedType type, LookupScope scope) {
        List<IndexedMember> members = typeMembers.declared(type).stream()
                .sorted(Comparator.comparingInt(member -> GROUP_ORDER.indexOf(member.descriptor().kind())))
                .toList();
        return new ClassView(
                type,
                hierarchy.directSupertypes(type, scope),
                firstParagraph(docs.typeDoc(type).description()),
                members,
                nestedTypes(type, scope),
                inherited(type, scope));
    }

    private List<LocatedType> nestedTypes(LocatedType type, LookupScope scope) {
        TypeName outer = type.name();
        return types.locate(types.repository().findNested(outer, List.of(type.artifact().id())), scope).stream()
                .filter(candidate -> candidate.name().outerName().equals(Optional.of(outer)))
                .filter(types::isApiVisible)
                .sorted(Comparator.comparing(candidate -> candidate.name().simpleName()))
                .toList();
    }

    private List<InheritedMembers> inherited(LocatedType type, LookupScope scope) {
        Map<Long, LocatedType> declaring = new LinkedHashMap<>();
        Map<Long, Set<String>> names = new LinkedHashMap<>();
        for (MemberMatch match : typeMembers.inherited(type, scope)) {
            declaring.putIfAbsent(match.declaringType().id(), match.declaringType());
            names.computeIfAbsent(match.declaringType().id(), id -> new LinkedHashSet<>())
                    .add(match.descriptor().name());
        }
        List<InheritedMembers> result = new ArrayList<>();
        declaring.forEach((id, supertype) -> result.add(new InheritedMembers(supertype, List.copyOf(names.get(id)))));
        return result;
    }

    /** The text up to the first blank line. */
    static String firstParagraph(String description) {
        String text = description.strip();
        int blank = text.indexOf("\n\n");
        return blank < 0 ? text : text.substring(0, blank).strip();
    }
}
