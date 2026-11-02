package io.github.vecluu.jdoc.query;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a {@code <type>} argument: an FQCN ({@code java.util.Optional}), a simple name ({@code Optional}),
 * {@code Outer.Inner}, {@code Outer$Inner} or {@code pkg.Outer.Inner}. Type arguments are ignored
 * ({@code Optional<T>} is {@code Optional}).
 *
 * <p>A {@code .} may separate packages or nested types, so a dotted name has several interpretations. Candidates
 * are ordered:
 * <ol>
 *   <li>if the first piece starts with an upper-case letter (or there is only one piece): a
 *       {@link TypeCandidate.Relative} match of all pieces in any package;</li>
 *   <li>the conventional split: package = pieces before the first upper-case piece;</li>
 *   <li>every other split, the top-level reading (fewest nested types) first.</li>
 * </ol>
 * A {@code $} always separates nested types (with the same rule as {@link TypeName}: only between two non-empty
 * pieces), so packages must end before it.
 *
 * <p>The resolver ({@code lookup.TypeResolver}) tries every exact candidate before any relative one, so this order
 * only matters within each kind.
 */
public final class TypeReferenceParser {

    /** Parses the argument; invalid syntax yields a failure with a descriptive message. */
    public ParseResult<TypeReference> parse(String input) {
        if (input == null || input.isBlank()) {
            return ParseResult.failure("empty type name");
        }
        String trimmed = input.strip();
        Optional<String> erased = GenericsEraser.erase(trimmed);
        if (erased.isEmpty()) {
            return ParseResult.failure("unbalanced '<' or '>' in type name '" + trimmed + "'");
        }
        String text = erased.get().strip();
        if (text.isEmpty() || text.chars().anyMatch(Character::isWhitespace)) {
            return ParseResult.failure("invalid type name '" + trimmed + "'");
        }
        List<String> pieces = new ArrayList<>();
        int firstNestedPiece = Integer.MAX_VALUE;
        for (String segment : text.split("\\.", -1)) {
            if (segment.isEmpty()) {
                return ParseResult.failure("empty name segment in type name '" + trimmed + "'");
            }
            if (!Identifiers.isIdentifier(segment)) {
                return ParseResult.failure("'" + segment + "' is not a valid Java name in type name '"
                        + trimmed + "'");
            }
            List<String> nested = TypeName.of(segment).simpleNames();
            if (nested.size() > 1) {
                firstNestedPiece = Math.min(firstNestedPiece, pieces.size() + 1);
            }
            pieces.addAll(nested);
        }
        return ParseResult.success(new TypeReference(trimmed, pieces, candidates(pieces, firstNestedPiece)));
    }

    private static List<TypeCandidate> candidates(List<String> pieces, int firstNestedPiece) {
        Set<TypeCandidate> candidates = new LinkedHashSet<>();
        boolean relative = pieces.size() == 1 || Identifiers.startsUppercase(pieces.getFirst());
        if (relative) {
            candidates.add(new TypeCandidate.Relative(pieces));
        }
        int lastSplit = Math.min(pieces.size() - 1, firstNestedPiece - 1);
        int firstSplit = relative ? 1 : 0;
        conventionalSplit(pieces)
                .filter(split -> split >= firstSplit && split <= lastSplit)
                .ifPresent(split -> candidates.add(exact(pieces, split)));
        for (int split = lastSplit; split >= firstSplit; split--) {
            candidates.add(exact(pieces, split));
        }
        return List.copyOf(candidates);
    }

    private static Optional<Integer> conventionalSplit(List<String> pieces) {
        for (int i = 0; i < pieces.size(); i++) {
            if (Identifiers.startsUppercase(pieces.get(i))) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static TypeCandidate exact(List<String> pieces, int split) {
        String packageName = String.join(".", pieces.subList(0, split));
        return new TypeCandidate.Exact(TypeName.of(packageName, pieces.subList(split, pieces.size())));
    }
}
