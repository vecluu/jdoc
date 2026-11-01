package io.github.vecluu.jdoc.text;

import java.util.List;

/**
 * One page of a paginated result, ready to print: header line, body lines and, for multi-page results, the footer.
 *
 * @param number 1-based page number
 * @param total  number of pages of the result
 * @param lines  every line of the page, header first
 */
public record Page(int number, int total, List<String> lines) {

    public Page {
        if (total < 1 || number < 1 || number > total) {
            throw new IllegalArgumentException("invalid page " + number + " of " + total);
        }
        lines = lines == null ? List.of() : List.copyOf(lines);
        lines.forEach(Lines::requireSingleLine);
    }

    /** True if more pages follow this one. */
    public boolean hasNext() {
        return number < total;
    }

    /** The page as text, lines joined by {@code \n}, no trailing line break. */
    public String text() {
        return String.join("\n", lines);
    }

    /** Characters the page occupies when printed with one line break after every line. */
    public int printedCharacters() {
        return lines.stream().mapToInt(Lines::characterCount).sum();
    }
}
