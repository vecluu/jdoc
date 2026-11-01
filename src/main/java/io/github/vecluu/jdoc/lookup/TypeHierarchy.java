package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.index.SupertypeRepository;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Supertype and subtype walks over the index.
 *
 * <p>Supertypes come in javadoc order: the superclass chain first (nearest first), then all interfaces breadth-first
 * (the type's own, then those of each superclass in chain order, then their super-interfaces), and
 * {@code java.lang.Object} last. A supertype that is not indexed in scope is kept by name and its own supertypes are
 * unknown. Supertypes resolve to the first artifact in classpath order and may be non-API types (e.g. a
 * package-private base class whose public members are inherited).
 */
@RequiredArgsConstructor
public final class TypeHierarchy {

    /** {@code java.lang.Object}. */
    public static final TypeName OBJECT = TypeName.of("java.lang.Object");

    private final ScopedTypes types;
    private final SupertypeRepository supertypes;

    /** Direct supertypes as stored (superclass first, then interfaces). */
    public List<Supertype> directSupertypes(LocatedType type, LookupScope scope) {
        return type.descriptor().supertypes().stream().map(name -> resolve(name, scope)).toList();
    }

    /** All transitive supertypes in javadoc order, without duplicates. */
    public List<Supertype> supertypes(LocatedType type, LookupScope scope) {
        List<Supertype> result = new ArrayList<>();
        Set<TypeName> seen = new LinkedHashSet<>();
        seen.add(type.name());
        boolean sawObject = false;
        List<LocatedType> classChain = new ArrayList<>();
        classChain.add(type);
        Optional<LocatedType> current = Optional.of(type);
        while (current.isPresent() && hasSuperclass(current.get())) {
            TypeName superclass = current.get().descriptor().supertypes().getFirst();
            if (!seen.add(superclass)) {
                break;
            }
            if (superclass.equals(OBJECT)) {
                sawObject = true;
                break;
            }
            Supertype resolved = resolve(superclass, scope);
            result.add(resolved);
            resolved.type().ifPresent(classChain::add);
            current = resolved.type();
        }
        Deque<TypeName> queue = new ArrayDeque<>();
        for (LocatedType member : classChain) {
            queue.addAll(interfacesOf(member));
        }
        while (!queue.isEmpty()) {
            TypeName next = queue.removeFirst();
            if (next.equals(OBJECT)) {
                sawObject = true;
                continue;
            }
            if (!seen.add(next)) {
                continue;
            }
            Supertype resolved = resolve(next, scope);
            result.add(resolved);
            resolved.type().ifPresent(known -> queue.addAll(interfacesOf(known)));
        }
        if (sawObject && !type.name().equals(OBJECT)) {
            result.add(resolve(OBJECT, scope));
        }
        return List.copyOf(result);
    }

    /**
     * Every known API-visible subtype or implementation in scope, transitively: direct subtypes first, then
     * breadth-first. The walk passes through non-API types (e.g. a package-private base class) without listing them.
     * A binary name in several artifacts is listed once (first in classpath order).
     */
    public List<Subtype> subtypes(LocatedType type, LookupScope scope) {
        List<Subtype> result = new ArrayList<>();
        Set<TypeName> seen = new LinkedHashSet<>();
        seen.add(type.name());
        Deque<TypeName> queue = new ArrayDeque<>();
        queue.add(type.name());
        boolean direct = true;
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<LocatedType> level = new ArrayList<>();
            for (int i = 0; i < levelSize; i++) {
                TypeName parent = queue.removeFirst();
                List<LocatedType> children =
                        ScopedTypes.firstPerName(types.locate(supertypes.directSubtypesOf(parent, scope.artifactIds()),
                                scope));
                for (LocatedType child : children) {
                    if (seen.add(child.name())) {
                        level.add(child);
                        queue.addLast(child.name());
                    }
                }
            }
            level.sort(ScopedTypes.byDisplayName(scope));
            for (LocatedType child : level) {
                if (types.isApiVisible(child)) {
                    result.add(new Subtype(child, direct));
                }
            }
            direct = false;
        }
        return List.copyOf(result);
    }

    private Supertype resolve(TypeName name, LookupScope scope) {
        return new Supertype(name, types.first(name, scope));
    }

    private static boolean hasSuperclass(LocatedType type) {
        TypeKind kind = type.descriptor().kind();
        boolean classLike = kind == TypeKind.CLASS || kind == TypeKind.ENUM || kind == TypeKind.RECORD;
        return classLike && !type.descriptor().supertypes().isEmpty();
    }

    private static List<TypeName> interfacesOf(LocatedType type) {
        List<TypeName> all = type.descriptor().supertypes();
        return hasSuperclass(type) ? all.subList(1, all.size()) : all;
    }
}
