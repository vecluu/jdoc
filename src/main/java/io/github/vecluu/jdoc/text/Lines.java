package io.github.vecluu.jdoc.text;

import java.util.Objects;

/** Line validation and size helpers shared within the package. */
final class Lines {
    private Lines() {
    }

    static String requireSingleLine(String line) {
        Objects.requireNonNull(line, "line must not be null");
        if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("line contains a line break: '" + line + "'");
        }
        return line;
    }

    /** Characters a line occupies in printed output, including its line break. */
    static int characterCount(String line) {
        return line.length() + 1;
    }
}
