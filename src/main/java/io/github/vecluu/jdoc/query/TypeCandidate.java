package io.github.vecluu.jdoc.query;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.List;
import java.util.Objects;

/** One interpretation of a type reference typed by the user, to be tried against the index. */
public sealed interface TypeCandidate permits TypeCandidate.Exact, TypeCandidate.Relative {

    /** The innermost simple name every matching type has, {@code Entry} for {@code Map.Entry}. */
    String simpleName();

    /** True if the given type satisfies this interpretation. */
    boolean matches(TypeName type);

    /**
     * A fully qualified interpretation: exactly this binary name.
     *
     * @param name the binary name, e.g. {@code java.util.Map$Entry}
     */
    record Exact(TypeName name) implements TypeCandidate {
        public Exact {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String simpleName() {
            return name.simpleName();
        }

        @Override
        public boolean matches(TypeName type) {
            return name.equals(type);
        }
    }

    /**
     * A package-less interpretation: any type, in any package, whose chain of simple names ends with these names.
     * {@code [Entry]} matches {@code java.util.Map$Entry} and {@code a.Entry}; {@code [Map, Entry]} matches
     * {@code java.util.Map$Entry} but not {@code a.Entry}.
     *
     * @param simpleNames simple names from the outermost given type inwards
     */
    record Relative(List<String> simpleNames) implements TypeCandidate {
        public Relative {
            simpleNames = List.copyOf(simpleNames);
            if (simpleNames.isEmpty()) {
                throw new IllegalArgumentException("at least one simple name is required");
            }
        }

        @Override
        public String simpleName() {
            return simpleNames.getLast();
        }

        @Override
        public boolean matches(TypeName type) {
            List<String> names = type.simpleNames();
            int offset = names.size() - simpleNames.size();
            return offset >= 0 && names.subList(offset, names.size()).equals(simpleNames);
        }
    }
}
