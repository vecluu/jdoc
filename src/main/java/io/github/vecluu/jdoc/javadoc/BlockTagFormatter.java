package io.github.vecluu.jdoc.javadoc;

/**
 * Formats block tags as single lines: {@code @param name text}, {@code @return text}, {@code @throws Type text},
 * {@code @since text}, {@code @deprecated text}, and any other tag via {@link #line(String, String)}.
 *
 * <p>Text arguments are plain text (run HTML through {@link HtmlTextFormatter} first); all whitespace, including
 * line breaks, collapses to single spaces. An empty text yields just the tag (and name). Stateless and thread-safe.
 */
public final class BlockTagFormatter {

    /** {@code @param name text}; type parameters are passed as {@code <T>}. */
    public String param(String name, String text) {
        return line("param", join(name, text));
    }

    /** {@code @return text}. */
    public String returns(String text) {
        return line("return", text);
    }

    /** {@code @throws Type text}; also use for {@code @exception}. */
    public String throwsTag(String type, String text) {
        return line("throws", join(type, text));
    }

    /** {@code @since text}. */
    public String since(String text) {
        return line("since", text);
    }

    /** {@code @deprecated text}. */
    public String deprecated(String text) {
        return line("deprecated", text);
    }

    /** {@code @see text}. */
    public String see(String text) {
        return line("see", text);
    }

    /**
     * Any tag as one line: {@code @tag text}.
     *
     * @param tag tag name with or without the leading {@code @}, e.g. {@code apiNote}
     */
    public String line(String tag, String text) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        String name = tag.strip();
        String head = name.startsWith("@") ? name : "@" + name;
        String body = collapse(text);
        return body.isEmpty() ? head : head + " " + body;
    }

    private static String join(String first, String rest) {
        return collapse(first) + " " + collapse(rest);
    }

    private static String collapse(String text) {
        return text == null ? "" : text.replaceAll("[\\s\\u00A0]+", " ").strip();
    }
}
