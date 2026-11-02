package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.query.MemberReference;
import java.util.List;
import java.util.Objects;

/** The outcome of looking up a {@code <type>#<name>[(<params>)]} argument. */
public sealed interface MemberLookup
        permits MemberLookup.Found, MemberLookup.MemberNotFound, MemberLookup.TypeNotResolved,
        MemberLookup.Invalid {

    /**
     * One or more members matched (every matching overload).
     *
     * @param type    the queried type
     * @param members matches in declaration order; all on the type itself, or all inherited
     */
    record Found(LocatedType type, List<MemberMatch> members) implements MemberLookup {
        public Found {
            Objects.requireNonNull(type, "type");
            members = List.copyOf(members);
            if (members.isEmpty()) {
                throw new IllegalArgumentException("a found lookup needs at least one member");
            }
        }
    }

    /**
     * The type exists but has no matching member.
     *
     * @param type        the queried type
     * @param reference   the parsed reference
     * @param suggestions member references worth trying: the overloads with that name when the parameters did not
     *                    match, otherwise members with similar names (may be empty)
     * @param protectedInherited inherited protected members that match the reference but are left out because the
     *                    type cannot be subclassed ({@link TypeMembers#inheritedForSubclassesOnly}); usually empty
     */
    record MemberNotFound(LocatedType type, MemberReference reference, List<String> suggestions,
                          List<MemberMatch> protectedInherited) implements MemberLookup {
        public MemberNotFound {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reference, "reference");
            suggestions = List.copyOf(suggestions);
            protectedInherited = List.copyOf(protectedInherited);
        }
    }

    /**
     * The type part did not resolve to exactly one type.
     *
     * @param typeLookup the {@link TypeLookup.NotFound}, {@link TypeLookup.Ambiguous} or {@link TypeLookup.Invalid}
     *                   outcome
     */
    record TypeNotResolved(TypeLookup typeLookup) implements MemberLookup {
        public TypeNotResolved {
            Objects.requireNonNull(typeLookup, "type lookup");
            if (typeLookup instanceof TypeLookup.Found) {
                throw new IllegalArgumentException("type was resolved");
            }
        }
    }

    /**
     * The argument is not a member reference.
     *
     * @param input   the argument as typed
     * @param message why it could not be parsed
     */
    record Invalid(String input, String message) implements MemberLookup {
        public Invalid {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(message, "message");
        }
    }
}
