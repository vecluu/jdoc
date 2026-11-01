package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.query.ParseResult;
import io.github.vecluu.jdoc.query.TypeCandidate;
import io.github.vecluu.jdoc.query.TypeReference;
import io.github.vecluu.jdoc.query.TypeReferenceParser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a {@code <type>} argument to one API-visible type, "not found" or "ambiguous".
 *
 * <p>Order: every fully qualified interpretation ({@code java.util.Map.Entry}, {@code java.util.Map$Entry}) first,
 * in the parser's order; the first that names an API type wins. Then package-less interpretations ({@code Entry},
 * {@code Map.Entry}): types whose chain of simple names ends with the given names (case-sensitive), so a simple
 * name also matches nested types; one distinct binary name is found, several are ambiguous. The same binary name in
 * several artifacts is not ambiguous: the first in classpath order wins, the others are reported as shadowed.
 */
@Slf4j
@RequiredArgsConstructor
public final class TypeResolver {

    private final ScopedTypes types;
    private final TypeReferenceParser parser;

    /** A resolver with the standard type-reference parser. */
    public TypeResolver(ScopedTypes types) {
        this(types, new TypeReferenceParser());
    }

    /** Parses and resolves the argument. */
    public TypeLookup resolve(String input, LookupScope scope) {
        ParseResult<TypeReference> parsed = parser.parse(input);
        if (parsed instanceof ParseResult.Failure<TypeReference> failure) {
            return new TypeLookup.Invalid(input == null ? "" : input.strip(), failure.message());
        }
        return resolve(parsed.orElseThrow(), scope);
    }

    /** Resolves an already parsed reference. */
    public TypeLookup resolve(TypeReference reference, LookupScope scope) {
        for (TypeCandidate candidate : reference.candidates()) {
            if (candidate instanceof TypeCandidate.Exact exact) {
                List<LocatedType> found = api(types.byBinaryName(exact.name(), scope));
                if (!found.isEmpty()) {
                    return found(reference, found);
                }
            }
        }
        for (TypeCandidate candidate : reference.candidates()) {
            if (candidate instanceof TypeCandidate.Relative relative) {
                List<LocatedType> found = api(types.locate(
                        types.repository().findBySimpleName(relative.simpleName(), scope.artifactIds()), scope))
                        .stream()
                        .filter(type -> relative.matches(type.name()))
                        .toList();
                List<LocatedType> distinct = ScopedTypes.firstPerName(found);
                if (distinct.size() == 1) {
                    return found(reference, found);
                }
                if (distinct.size() > 1) {
                    return new TypeLookup.Ambiguous(reference.input(),
                            distinct.stream().sorted(ScopedTypes.byDisplayName(scope)).toList());
                }
            }
        }
        List<LocatedType> suggestions = ScopedTypes.firstPerName(api(types.locate(
                types.repository().findBySimpleName(reference.simpleName(), scope.artifactIds()), scope)))
                .stream()
                .sorted(ScopedTypes.byDisplayName(scope))
                .toList();
        return new TypeLookup.NotFound(reference.input(), suggestions);
    }

    private List<LocatedType> api(List<LocatedType> located) {
        return located.stream().filter(types::isApiVisible).toList();
    }

    private static TypeLookup found(TypeReference reference, List<LocatedType> sameName) {
        LocatedType first = sameName.getFirst();
        List<LocatedType> shadowed = sameName.subList(1, sameName.size());
        if (!shadowed.isEmpty()) {
            log.debug("{} is in several artifacts; using {} ({} shadowed)", first.name(), first.label(),
                    shadowed.size());
        }
        return new TypeLookup.Found(reference.input(), first, shadowed);
    }
}
