package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.index.TypeRepository;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Type access shared by the lookup services: scoped reads that return {@link LocatedType}s in classpath order, and
 * the API-visibility rule (public or protected, with an API-visible enclosing chain).
 *
 * <p>Caches visibility answers per type; not thread-safe.
 */
public final class ScopedTypes {

    private final TypeRepository types;
    private final Map<String, Boolean> apiVisible = new HashMap<>();

    public ScopedTypes(TypeRepository types) {
        this.types = types;
    }

    /** The underlying repository, for queries not wrapped here. */
    public TypeRepository repository() {
        return types;
    }

    /** Every type with this binary name in scope (any visibility), in classpath order. */
    public List<LocatedType> byBinaryName(TypeName name, LookupScope scope) {
        return locate(types.findByBinaryName(name, scope.artifactIds()), scope);
    }

    /** The first type with this binary name in classpath order (any visibility). */
    public Optional<LocatedType> first(TypeName name, LookupScope scope) {
        return byBinaryName(name, scope).stream().findFirst();
    }

    /**
     * Attaches the scope's artifacts to index rows, drops rows outside the scope, and orders the result by classpath
     * rank (stable: rows of one artifact keep their order).
     */
    public List<LocatedType> locate(Collection<IndexedType> rows, LookupScope scope) {
        List<LocatedType> located = new ArrayList<>();
        for (IndexedType row : rows) {
            scope.artifact(row.artifactId()).ifPresent(artifact -> located.add(new LocatedType(row, artifact)));
        }
        located.sort(scope.byRank(type -> type.artifact().id()));
        return located;
    }

    /**
     * Keeps the first type (in the given order) of each binary name: the same FQCN in a later classpath artifact is
     * shadowed, as on a real classpath.
     */
    public static List<LocatedType> firstPerName(List<LocatedType> located) {
        Map<TypeName, LocatedType> first = new LinkedHashMap<>();
        for (LocatedType type : located) {
            first.putIfAbsent(type.name(), type);
        }
        return List.copyOf(first.values());
    }

    /** Orders by display name, then classpath rank. */
    public static Comparator<LocatedType> byDisplayName(LookupScope scope) {
        return Comparator.comparing((LocatedType type) -> type.name().displayName())
                .thenComparing(scope.byRank(type -> type.artifact().id()));
    }

    /** True if the type is public or protected and so is every enclosing type. */
    public boolean isApiVisible(LocatedType type) {
        return isApiVisible(type.type());
    }

    /** True if the type is public or protected and so is every enclosing type (looked up in its own artifact). */
    public boolean isApiVisible(IndexedType type) {
        String key = type.artifactId() + ":" + type.name().binaryName();
        Boolean cached = apiVisible.get(key);
        if (cached != null) {
            return cached;
        }
        boolean visible = type.descriptor().visibility().isApi() && type.name().outerName()
                .map(outer -> types.findByBinaryName(outer, List.of(type.artifactId())).stream()
                        .findFirst()
                        .map(this::isApiVisible)
                        .orElse(true))
                .orElse(true);
        apiVisible.put(key, visible);
        return visible;
    }
}
