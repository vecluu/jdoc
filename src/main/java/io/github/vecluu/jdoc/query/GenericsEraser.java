package io.github.vecluu.jdoc.query;

import java.util.Optional;

/** Removes type arguments ({@code <...>}, possibly nested) from type text. */
final class GenericsEraser {
    private GenericsEraser() {
    }

    /** The text without any {@code <...>} groups, or empty if the angle brackets are unbalanced. */
    static Optional<String> erase(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
                if (depth < 0) {
                    return Optional.empty();
                }
            } else if (depth == 0) {
                result.append(c);
            }
        }
        return depth == 0 ? Optional.of(result.toString()) : Optional.empty();
    }
}
