package io.github.vecluu.jdoc.text;

import java.util.List;
import java.util.Objects;

/**
 * A group of output lines that should stay on one page where possible, e.g. one method's Javadoc.
 *
 * <p>Lines must not contain line breaks; a block may be empty.
 *
 * @param lines the lines of the block
 */
public record TextBlock(List<String> lines) {

    public TextBlock {
        lines = lines == null ? List.of() : List.copyOf(lines);
        for (String line : lines) {
            Lines.requireSingleLine(line);
        }
    }

    /** A block of the given lines. */
    public static TextBlock of(String... lines) {
        return new TextBlock(List.of(lines));
    }

    /** A block of a single line. */
    public static TextBlock line(String line) {
        return new TextBlock(List.of(Objects.requireNonNull(line, "line")));
    }

    /** True if the block has no lines. */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Characters this block adds to a page, one line break per line included. */
    int characterCount() {
        return lines.stream().mapToInt(Lines::characterCount).sum();
    }
}
