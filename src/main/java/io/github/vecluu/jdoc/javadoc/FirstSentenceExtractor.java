package io.github.vecluu.jdoc.javadoc;

import java.util.Locale;
import java.util.Set;

/**
 * Extracts the first sentence of a plain-text description (the output of {@link HtmlTextFormatter}), following
 * javadoc's default rule: the text up to and including the first period that is followed by whitespace or the end.
 *
 * <p>Refinements: common abbreviations ({@code e.g.}, {@code i.e.}, {@code etc.} is <em>not</em> one) do not end a
 * sentence; periods inside backtick code spans are ignored; the sentence also ends at the end of the first
 * paragraph (blank line), before a list item line ({@code - item}) and before an indented ({@code <pre>}) block.
 * The result is one line with whitespace collapsed. Stateless and thread-safe.
 */
public final class FirstSentenceExtractor {

    private static final Set<String> ABBREVIATIONS = Set.of(
            "e.g.", "i.e.", "vs.", "cf.", "approx.", "incl.", "resp.", "a.k.a.", "et al.", "viz.", "ca.");

    /** The first sentence of {@code text}; empty for {@code null} or blank text. */
    public String extract(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String paragraph = firstParagraph(text.strip());
        boolean inCode = false;
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            if (c == '`') {
                inCode = !inCode;
            } else if (c == '.' && !inCode && endsSentence(paragraph, i)) {
                return collapse(paragraph.substring(0, i + 1));
            }
        }
        return collapse(paragraph);
    }

    /** The text before the first blank line, list item line or indented line. */
    private static String firstParagraph(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.strip().startsWith("- ") || Character.isWhitespace(line.charAt(0))) {
                break;
            }
            sb.append('\n').append(line);
        }
        return sb.toString();
    }

    private static boolean endsSentence(String text, int periodIndex) {
        if (periodIndex + 1 < text.length() && !Character.isWhitespace(text.charAt(periodIndex + 1))) {
            return false;
        }
        return !isAbbreviation(text, periodIndex);
    }

    private static boolean isAbbreviation(String text, int periodIndex) {
        String before = text.substring(0, periodIndex + 1).toLowerCase(Locale.ROOT);
        for (String abbreviation : ABBREVIATIONS) {
            if (before.endsWith(abbreviation)) {
                int start = before.length() - abbreviation.length();
                if (start == 0 || !Character.isLetterOrDigit(before.charAt(start - 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String collapse(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
