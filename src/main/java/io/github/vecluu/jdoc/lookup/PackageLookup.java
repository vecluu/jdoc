package io.github.vecluu.jdoc.lookup;

import java.util.List;
import java.util.Objects;

/** The outcome of {@code jdoc package <pkg>}. */
public sealed interface PackageLookup permits PackageLookup.Found, PackageLookup.NotFound {

    /**
     * The package has API types in scope.
     *
     * @param view the package's doc and types
     */
    record Found(PackageView view) implements PackageLookup {
        public Found {
            Objects.requireNonNull(view, "view");
        }
    }

    /**
     * No API type of that package is in scope.
     *
     * @param name        the package as typed
     * @param suggestions package names in scope that contain or end with the text (may be empty)
     */
    record NotFound(String name, List<String> suggestions) implements PackageLookup {
        public NotFound {
            Objects.requireNonNull(name, "name");
            suggestions = List.copyOf(suggestions);
        }
    }
}
