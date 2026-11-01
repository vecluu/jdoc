package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.query.MemberReference;
import io.github.vecluu.jdoc.query.MemberReferenceParser;
import io.github.vecluu.jdoc.query.NamePattern;
import io.github.vecluu.jdoc.query.OverloadMatcher;
import io.github.vecluu.jdoc.query.ParseResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Finds the members a {@code <type>#<name>[(<params>)]} argument means (for {@code jdoc method} and
 * {@code jdoc source}).
 *
 * <p>Candidates are the queried type's own API members; only if none matches are inherited members tried
 * (constructors are never inherited), each reported with its declaring type. Every matching overload is returned.
 */
@RequiredArgsConstructor
public final class MemberLookupService {

    private static final int MAX_SUGGESTIONS = 10;

    private final TypeResolver typeResolver;
    private final TypeMembers typeMembers;
    private final MemberReferenceParser parser;
    private final OverloadMatcher overloads;

    /** A service with the standard parser and overload matcher. */
    public MemberLookupService(TypeResolver typeResolver, TypeMembers typeMembers) {
        this(typeResolver, typeMembers, new MemberReferenceParser(), new OverloadMatcher());
    }

    /** Parses the argument and looks the member up. */
    public MemberLookup lookup(String input, LookupScope scope) {
        ParseResult<MemberReference> parsed = parser.parse(input);
        if (parsed instanceof ParseResult.Failure<MemberReference> failure) {
            return new MemberLookup.Invalid(input == null ? "" : input.strip(), failure.message());
        }
        return lookup(parsed.orElseThrow(), scope);
    }

    /** Looks up an already parsed reference. */
    public MemberLookup lookup(MemberReference reference, LookupScope scope) {
        TypeLookup typeLookup = typeResolver.resolve(reference.type(), scope);
        if (!(typeLookup instanceof TypeLookup.Found found)) {
            return new MemberLookup.TypeNotResolved(typeLookup);
        }
        LocatedType type = found.type();
        List<MemberMatch> own = typeMembers.declared(type).stream()
                .map(member -> new MemberMatch(member, type, false))
                .toList();
        List<MemberMatch> ownMatches = matching(own, reference);
        if (!ownMatches.isEmpty()) {
            return new MemberLookup.Found(type, ownMatches);
        }
        List<MemberMatch> inherited = typeMembers.inherited(type, scope);
        List<MemberMatch> inheritedMatches = reference.constructor() ? List.of() : matching(inherited, reference);
        if (!inheritedMatches.isEmpty()) {
            return new MemberLookup.Found(type, inheritedMatches);
        }
        List<MemberMatch> all = new ArrayList<>(own);
        all.addAll(inherited);
        return new MemberLookup.MemberNotFound(type, reference, suggestions(all, reference));
    }

    private List<MemberMatch> matching(List<MemberMatch> candidates, MemberReference reference) {
        return candidates.stream().filter(match -> overloads.matches(reference, match.descriptor())).toList();
    }

    private static List<String> suggestions(List<MemberMatch> candidates, MemberReference reference) {
        List<String> sameName = candidates.stream()
                .filter(match -> sameKindAndName(match.descriptor(), reference))
                .map(MemberMatch::reference)
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .toList();
        if (!sameName.isEmpty()) {
            return sameName;
        }
        NamePattern pattern = new NamePattern(reference.name());
        Set<String> similar = new LinkedHashSet<>();
        candidates.stream()
                .filter(match -> pattern.matches(match.descriptor().name()))
                .sorted(Comparator.comparing((MemberMatch match) -> pattern.match(match.descriptor().name())
                                .map(score -> score.kind().ordinal()).orElse(Integer.MAX_VALUE)))
                .map(MemberMatch::reference)
                .forEach(similar::add);
        return similar.stream().limit(MAX_SUGGESTIONS).toList();
    }

    private static boolean sameKindAndName(MemberDescriptor member, MemberReference reference) {
        return member.name().equals(reference.name())
                && reference.constructor() == (member.kind() == MemberKind.CONSTRUCTOR);
    }
}
