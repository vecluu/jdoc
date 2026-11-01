package io.github.vecluu.jdoc.query;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalizes parameter type text to the form used for overload matching: type arguments erased, qualified names
 * reduced to the simple name ({@code java.util.Map.Entry} and {@code Map$Entry} become {@code Entry}), varargs
 * written as an array ({@code String...} becomes {@code String[]}), whitespace removed.
 */
public final class ParameterTypes {

    private static final Pattern VARARGS = Pattern.compile("\\s*\\.\\.\\.");
    private static final Pattern SPACE_AROUND_DOT = Pattern.compile("\\s*\\.\\s*");
    private static final Pattern SPACE_AROUND_OPEN_BRACKET = Pattern.compile("\\s*\\[\\s*");
    private static final Pattern SPACE_BEFORE_CLOSE_BRACKET = Pattern.compile("\\s*]");
    private static final Pattern ARRAY_SUFFIX = Pattern.compile("(\\[])*$");

    private ParameterTypes() {
    }

    /**
     * Normalizes a parameter as typed by the user. A trailing parameter name is tolerated ({@code int count}).
     *
     * @return the normalized type, or a failure describing what is wrong
     */
    public static ParseResult<String> normalize(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return ParseResult.failure("empty parameter type");
        }
        Optional<String> erased = GenericsEraser.erase(trimmed);
        if (erased.isEmpty()) {
            return ParseResult.failure("unbalanced '<' or '>' in parameter type '" + trimmed + "'");
        }
        String compact = VARARGS.matcher(erased.get().strip()).replaceAll("[]");
        compact = SPACE_AROUND_DOT.matcher(compact).replaceAll(".");
        compact = SPACE_AROUND_OPEN_BRACKET.matcher(compact).replaceAll("[");
        compact = SPACE_BEFORE_CLOSE_BRACKET.matcher(compact).replaceAll("]").strip();
        String[] tokens = compact.split("\\s+");
        if (tokens.length == 2 && Identifiers.isIdentifier(tokens[1])) {
            compact = tokens[0];
        } else if (tokens.length != 1) {
            return ParseResult.failure("invalid parameter type '" + trimmed + "'");
        }
        String dimensions = ARRAY_SUFFIX.matcher(compact).results().findFirst().orElseThrow().group();
        String base = compact.substring(0, compact.length() - dimensions.length());
        if (!isQualifiedName(base)) {
            return ParseResult.failure("invalid parameter type '" + trimmed + "'");
        }
        return ParseResult.success(TypeName.of(base).simpleName() + dimensions);
    }

    /** Normalizes type text from an index entry, falling back to the stripped text if it cannot be parsed. */
    public static String normalizeLenient(String text) {
        return normalize(text).value().orElse(text == null ? "" : text.strip());
    }

    private static boolean isQualifiedName(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (String segment : text.split("\\.", -1)) {
            if (!Identifiers.isIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }
}
