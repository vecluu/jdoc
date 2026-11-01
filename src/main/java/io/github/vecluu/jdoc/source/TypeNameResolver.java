package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Best-effort resolution of type names written in one source file to binary names, without attribution.
 *
 * <p>The first segment of a written name ({@code Map} in {@code Map.Entry}) is looked up in this order:
 * <ol>
 *   <li>types declared in the same file, from the innermost enclosing type outwards, then top-level;</li>
 *   <li>single-type imports;</li>
 *   <li>the same package, if {@link KnownTypes} confirms the type;</li>
 *   <li>{@code java.lang} (built-in list of its public types, or confirmed by {@link KnownTypes});</li>
 *   <li>on-demand imports confirmed by {@link KnownTypes}, in import order;</li>
 *   <li>otherwise a guess: the first on-demand import if there is one, else the same package.</li>
 * </ol>
 * Remaining segments are nested types. A name whose first segment starts with a lowercase letter and that has more
 * segments is taken as fully qualified; its type part starts at the first segment with an uppercase initial.
 * Member types inherited from supertypes are not found (they resolve like unknown names).
 */
final class TypeNameResolver {

    private final String packageName;
    private final Set<TypeName> fileTypes;
    private final Map<String, TypeName> singleImports;
    private final List<String> onDemandImports;
    private final KnownTypes knownTypes;

    TypeNameResolver(String packageName, Set<TypeName> fileTypes, List<String> singleTypeImports,
                     List<String> onDemandImports, KnownTypes knownTypes) {
        this.packageName = packageName;
        this.fileTypes = Set.copyOf(fileTypes);
        Map<String, TypeName> imports = new HashMap<>();
        for (String imported : singleTypeImports) {
            TypeName name = qualified(imported);
            imports.putIfAbsent(name.simpleName(), name);
        }
        this.singleImports = Map.copyOf(imports);
        this.onDemandImports = List.copyOf(onDemandImports);
        this.knownTypes = knownTypes;
    }

    /**
     * Resolves a written type name such as {@code Entry}, {@code Map.Entry} or {@code java.util.Map.Entry}.
     *
     * @param written   dotted name as written, without type arguments
     * @param enclosing the innermost type whose body contains the name, if any
     */
    TypeName resolve(String written, Optional<TypeName> enclosing) {
        String[] segments = written.split("\\.");
        if (segments.length > 1 && Character.isLowerCase(segments[0].charAt(0))) {
            return qualified(written);
        }
        TypeName result = resolveSimple(segments[0], enclosing);
        for (int i = 1; i < segments.length; i++) {
            result = result.nested(segments[i]);
        }
        return result;
    }

    private TypeName resolveSimple(String simple, Optional<TypeName> enclosing) {
        Optional<TypeName> scope = enclosing;
        while (scope.isPresent()) {
            TypeName current = scope.get();
            TypeName member = current.nested(simple);
            if (fileTypes.contains(member)) {
                return member;
            }
            if (current.simpleName().equals(simple)) {
                return current;
            }
            scope = current.outerName();
        }
        TypeName samePackage = TypeName.of(packageName, List.of(simple));
        if (fileTypes.contains(samePackage)) {
            return samePackage;
        }
        TypeName imported = singleImports.get(simple);
        if (imported != null) {
            return imported;
        }
        if (knownTypes.contains(samePackage)) {
            return samePackage;
        }
        TypeName javaLang = TypeName.of("java.lang", List.of(simple));
        if (JavaLangTypes.contains(simple) || knownTypes.contains(javaLang)) {
            return javaLang;
        }
        List<TypeName> candidates = new ArrayList<>();
        for (String prefix : onDemandImports) {
            TypeName candidate = qualified(prefix + "." + simple);
            if (knownTypes.contains(candidate)) {
                return candidate;
            }
            candidates.add(candidate);
        }
        return candidates.isEmpty() ? samePackage : candidates.getFirst();
    }

    /**
     * A fully qualified dotted name: segments before the first one with an uppercase initial are the package
     * (if no segment has one, only the last segment is a type).
     */
    static TypeName qualified(String dotted) {
        String[] segments = dotted.split("\\.");
        int firstType = segments.length - 1;
        for (int i = 0; i < segments.length; i++) {
            if (!segments[i].isEmpty() && Character.isUpperCase(segments[i].charAt(0))) {
                firstType = i;
                break;
            }
        }
        return TypeName.fromDisplayName(dotted, segments.length - 1 - firstType);
    }
}
