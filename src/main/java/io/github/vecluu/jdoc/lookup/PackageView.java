package io.github.vecluu.jdoc.lookup;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything {@code jdoc package} shows.
 *
 * @param name  the package name
 * @param doc   the package documentation, if any artifact has it
 * @param types API-visible types of the package (top-level and nested), by relative name ({@code Map},
 *              {@code Map.Entry}); first sentences are in their descriptors
 */
public record PackageView(String name, Optional<PackageDoc> doc, List<LocatedType> types) {
    public PackageView {
        Objects.requireNonNull(name, "name");
        doc = doc == null ? Optional.empty() : doc;
        types = List.copyOf(types);
    }
}
