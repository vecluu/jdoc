package io.github.vecluu.jdoc.lookup;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;

/** {@code jdoc package <pkg>}: the package doc and its API types. */
@RequiredArgsConstructor
public final class PackageService {

    private static final int MAX_SUGGESTIONS = 10;

    private final ScopedTypes types;
    private final DocLoader docs;

    /** Describes the package, or reports it as not found with similar package names. */
    public PackageLookup describe(String packageName, LookupScope scope) {
        String name = packageName == null ? "" : packageName.strip();
        List<LocatedType> all = types.locate(types.repository().findByPackage(name, scope.artifactIds()), scope);
        List<LocatedType> api = ScopedTypes.firstPerName(all.stream().filter(types::isApiVisible).toList()).stream()
                .sorted(Comparator.comparing((LocatedType type) -> type.name().relativeName()))
                .toList();
        if (api.isEmpty()) {
            return new PackageLookup.NotFound(name, suggestions(name, scope));
        }
        return new PackageLookup.Found(new PackageView(name, docs.packageDoc(name, all), api));
    }

    private List<String> suggestions(String name, LookupScope scope) {
        if (name.isEmpty()) {
            return List.of();
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return types.repository().listPackages(scope.artifactIds()).stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).contains(lower))
                .sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .limit(MAX_SUGGESTIONS)
                .toList();
    }
}
