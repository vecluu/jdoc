package io.github.vecluu.jdoc.index;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Passes a set of ids (artifact or type ids) of any size to SQLite as one JSON-array parameter, expanded with {@code json_each}.
 * This avoids SQLite's bound-parameter limit and per-query temporary tables; SQLite materialises the list into an
 * ephemeral index, so membership tests stay cheap for large scopes.
 */
final class IdList {

    /** SQL fragment to append after a column: {@code column IN (...)}, taking one parameter. */
    static final String IN_LIST = " IN (SELECT value FROM json_each(?))";

    private IdList() {
    }

    /** The JSON array parameter for {@link #IN_LIST}. */
    static String json(Collection<Long> ids) {
        Objects.requireNonNull(ids, "artifact ids");
        return ids.stream().map(id -> Long.toString(Objects.requireNonNull(id, "artifact id")))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** {@code LIKE} pattern matching {@code text} anywhere, with {@code \} as escape character. */
    static String containsPattern(String text) {
        Objects.requireNonNull(text, "text");
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
