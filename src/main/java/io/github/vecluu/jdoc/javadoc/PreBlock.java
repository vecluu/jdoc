package io.github.vecluu.jdoc.javadoc;

import java.util.ArrayList;
import java.util.List;

/** Normalization of {@code <pre>} block content: verbatim except outer blank lines and common indentation. */
final class PreBlock {
    private PreBlock() {
    }

    /**
     * Turns non-breaking spaces into spaces, strips trailing whitespace, drops leading and trailing blank lines, and
     * removes the indentation common to all non-blank lines.
     */
    static String normalize(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : raw.replace(' ', ' ').replace("\r\n", "\n").split("\n", -1)) {
            lines.add(line.stripTrailing());
        }
        while (!lines.isEmpty() && lines.getFirst().isEmpty()) {
            lines.removeFirst();
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        int indent = lines.stream()
                .filter(line -> !line.isEmpty())
                .mapToInt(PreBlock::indentation)
                .min()
                .orElse(0);
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(line.isEmpty() ? line : line.substring(indent));
        }
        return String.join("\n", result);
    }

    private static int indentation(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }
}
