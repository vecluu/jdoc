package io.github.vecluu.jdoc.query;

import java.util.Comparator;
import java.util.Objects;

/**
 * The score of a name that matched a {@link NamePattern}. Natural order is best first: by {@link MatchKind}, then
 * shorter names first. Callers break remaining ties (e.g. alphabetically).
 *
 * @param kind       how the name matched
 * @param nameLength length of the matched name
 */
public record NameMatch(MatchKind kind, int nameLength) implements Comparable<NameMatch> {

    private static final Comparator<NameMatch> ORDER =
            Comparator.comparing(NameMatch::kind).thenComparingInt(NameMatch::nameLength);

    public NameMatch {
        Objects.requireNonNull(kind, "kind");
    }

    @Override
    public int compareTo(NameMatch other) {
        return ORDER.compare(this, other);
    }
}
