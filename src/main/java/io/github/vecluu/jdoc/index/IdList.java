package io.github.vecluu.jdoc.index;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Passes a set of ids (artifact or type ids) or strings of any size to SQLite as one JSON-array parameter, expanded with {@code json_each}.
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

    /**
     * A JSON array of strings for {@link #IN_LIST} (e.g. member names); quotes, backslashes and control characters
     * are escaped, everything else is passed through as UTF-16 text.
     */
    static String jsonStrings(Collection<String> values) {
        Objects.requireNonNull(values, "values");
        StringBuilder json = new StringBuilder("[");
        for (String value : values) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"');
            Objects.requireNonNull(value, "value").chars().forEach(c -> {
                if (c == '"' || c == '\\') {
                    json.append('\\').append((char) c);
                } else if (c < 0x20) {
                    json.append(String.format("\\u%04x", c));
                } else {
                    json.append((char) c);
                }
            });
            json.append('"');
        }
        return json.append(']').toString();
    }

    /** {@code LIKE} pattern matching {@code text} anywhere, with {@code \} as escape character. */
    static String containsPattern(String text) {
        Objects.requireNonNull(text, "text");
        return "%" + escapeLike(text) + "%";
    }

    /**
     * {@code LIKE} pattern matching the characters of {@code text} in order with anything in between
     * ({@code oET} → {@code %o%E%T%}), with {@code \} as escape character; an empty text matches everything.
     */
    static String subsequencePattern(String text) {
        Objects.requireNonNull(text, "text");
        StringBuilder like = new StringBuilder("%");
        text.codePoints().forEach(c -> like.append(escapeLike(Character.toString(c))).append('%'));
        return like.toString();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
