package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.index.MemberRepository;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * Declared and inherited API members of a type.
 *
 * <p>Inherited members are collected over {@link TypeHierarchy#supertypes} in javadoc order. A supertype member is
 * inherited unless it is a constructor, a static method of an interface (not inherited in Java), or overridden or
 * hidden by a member of the type itself or of a closer supertype ({@link OverrideMatcher}).
 *
 * <p>Inherited <em>protected</em> members are usable only by subclasses. A type that users cannot subclass (a
 * {@code final} or {@code sealed} class, an enum or a record) therefore does not list them; they are reported
 * separately by {@link #inheritedForSubclassesOnly}. Interfaces never inherit protected members, and a type's own
 * protected members are always part of {@link #declared}.
 */
@RequiredArgsConstructor
public final class TypeMembers {

    private final MemberRepository members;
    private final TypeHierarchy hierarchy;
    private final OverrideMatcher overrides;

    /** The type's own public and protected members, in declaration order. */
    public List<IndexedMember> declared(LocatedType type) {
        return members.membersOf(type.id()).stream().filter(member -> member.descriptor().visibility().isApi())
                .toList();
    }

    /**
     * Members inherited from supertypes that callers can use through this type, nearest supertype first,
     * declaration order within a supertype.
     */
    public List<MemberMatch> inherited(LocatedType type, LookupScope scope) {
        boolean subclassable = canBeSubclassed(type);
        return allInherited(type, scope).stream().filter(match -> subclassable || !isProtected(match)).toList();
    }

    /**
     * Inherited protected members that only a subclass could use, for a type that cannot be subclassed; empty for
     * every other type. These are the members {@link #inherited} leaves out.
     */
    public List<MemberMatch> inheritedForSubclassesOnly(LocatedType type, LookupScope scope) {
        if (canBeSubclassed(type)) {
            return List.of();
        }
        return allInherited(type, scope).stream().filter(TypeMembers::isProtected).toList();
    }

    /**
     * Whether code outside the type's library can declare a subclass: false for enums, records and {@code final} or
     * {@code sealed} classes. Interfaces and annotations count as subclassable (they inherit no protected members).
     */
    static boolean canBeSubclassed(LocatedType type) {
        TypeKind kind = type.descriptor().kind();
        if (kind == TypeKind.ENUM || kind == TypeKind.RECORD) {
            return false;
        }
        List<String> modifiers = List.of(type.descriptor().modifiers().strip().split("\\s+"));
        return !(kind == TypeKind.CLASS && (modifiers.contains("final") || modifiers.contains("sealed")));
    }

    private static boolean isProtected(MemberMatch match) {
        return match.descriptor().visibility() == Visibility.PROTECTED;
    }

    private List<MemberMatch> allInherited(LocatedType type, LookupScope scope) {
        List<LocatedType> supertypes = hierarchy.supertypes(type, scope).stream()
                .flatMap(supertype -> supertype.type().stream())
                .toList();
        Map<Long, List<IndexedMember>> bySupertype =
                members.membersOf(supertypes.stream().map(LocatedType::id).toList());
        List<MemberDescriptor> visible = new ArrayList<>(declared(type).stream().map(IndexedMember::descriptor)
                .toList());
        List<MemberMatch> result = new ArrayList<>();
        for (LocatedType supertype : supertypes) {
            for (IndexedMember member : bySupertype.getOrDefault(supertype.id(), List.of())) {
                MemberDescriptor descriptor = member.descriptor();
                if (isInheritable(supertype, descriptor)
                        && visible.stream().noneMatch(closer -> overrides.overrides(closer, descriptor))) {
                    visible.add(descriptor);
                    result.add(new MemberMatch(member, supertype, true));
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean isInheritable(LocatedType supertype, MemberDescriptor member) {
        if (!member.visibility().isApi() || member.kind() == MemberKind.CONSTRUCTOR) {
            return false;
        }
        TypeKind kind = supertype.descriptor().kind();
        boolean interfaceLike = kind == TypeKind.INTERFACE || kind == TypeKind.ANNOTATION;
        return !(interfaceLike && member.isStatic() && member.kind() == MemberKind.METHOD);
    }
}
