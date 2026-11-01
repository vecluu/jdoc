package io.github.vecluu.jdoc.lookup;

import java.util.List;

/**
 * The outcome of {@code jdoc find}.
 *
 * @param pattern   the pattern as typed
 * @param hits      best first, at most {@link FindService#MAX_HITS}
 * @param truncated true if more names matched than were returned
 */
public record FindResult(String pattern, List<FindHit> hits, boolean truncated) {
    public FindResult {
        pattern = pattern == null ? "" : pattern;
        hits = List.copyOf(hits);
    }

    /** True if nothing matched. */
    public boolean isEmpty() {
        return hits.isEmpty();
    }
}
