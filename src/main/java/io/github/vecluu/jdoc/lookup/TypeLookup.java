package io.github.vecluu.jdoc.lookup;

import java.util.List;
import java.util.Objects;

/** The outcome of resolving a {@code <type>} argument. */
public sealed interface TypeLookup
        permits TypeLookup.Found, TypeLookup.NotFound, TypeLookup.Ambiguous, TypeLookup.Invalid {

    /** The argument as typed. */
    String input();

    /**
     * Exactly one type matched.
     *
     * @param input    the argument as typed
     * @param type     the type, from the first artifact in classpath order that has it
     * @param shadowed the same binary name in later classpath artifacts (normally empty); not an ambiguity
     */
    record Found(String input, LocatedType type, List<LocatedType> shadowed) implements TypeLookup {
        public Found {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(type, "type");
            shadowed = List.copyOf(shadowed);
        }
    }

    /**
     * No API-visible type matched.
     *
     * @param input       the argument as typed
     * @param suggestions API types whose simple name equals the argument's ignoring case (may be empty)
     */
    record NotFound(String input, List<LocatedType> suggestions) implements TypeLookup {
        public NotFound {
            Objects.requireNonNull(input, "input");
            suggestions = List.copyOf(suggestions);
        }
    }

    /**
     * Several different types matched (a simple or partial name); no docs are shown.
     *
     * @param input      the argument as typed
     * @param candidates one entry per distinct binary name, ordered by display name
     */
    record Ambiguous(String input, List<LocatedType> candidates) implements TypeLookup {
        public Ambiguous {
            Objects.requireNonNull(input, "input");
            candidates = List.copyOf(candidates);
        }
    }

    /**
     * The argument is not a type name.
     *
     * @param input   the argument as typed
     * @param message why it could not be parsed
     */
    record Invalid(String input, String message) implements TypeLookup {
        public Invalid {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(message, "message");
        }
    }
}
