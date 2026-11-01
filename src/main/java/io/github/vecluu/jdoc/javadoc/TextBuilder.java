package io.github.vecluu.jdoc.javadoc;

/**
 * Accumulates plain text with collapsed whitespace and requested line/paragraph breaks, which are only written
 * once further content follows (so there are never trailing or doubled breaks).
 */
final class TextBuilder {

    private final StringBuilder sb = new StringBuilder();
    private int pendingBreaks;
    private boolean pendingSpace;
    private boolean lineStart = true;

    /** Appends flowing text: whitespace runs (including non-breaking spaces) collapse to one space. */
    void text(String raw) {
        if (raw.isEmpty()) {
            return;
        }
        String collapsed = raw.replaceAll("[\\s\\u00A0]+", " ");
        String core = collapsed.strip();
        if (core.isEmpty()) {
            pendingSpace = true;
            return;
        }
        boolean leading = collapsed.charAt(0) == ' ';
        flushBreaks();
        if ((leading || pendingSpace) && !lineStart) {
            sb.append(' ');
        }
        sb.append(core);
        lineStart = false;
        pendingSpace = collapsed.charAt(collapsed.length() - 1) == ' ';
    }

    /** Appends a block of text as is, on its own lines. */
    void verbatim(String block) {
        if (block.isEmpty()) {
            return;
        }
        flushBreaks();
        if (!lineStart) {
            sb.append('\n');
        }
        sb.append(block);
        lineStart = false;
        pendingSpace = false;
    }

    /** Starts a new line with the given prefix (e.g. {@code "- "}). */
    void linePrefix(String prefix) {
        flushBreaks();
        if (!lineStart) {
            sb.append('\n');
        }
        sb.append(prefix);
        lineStart = true;
        pendingSpace = false;
    }

    /** Requests that the next content starts on a new line. */
    void lineBreak() {
        requestBreaks(1);
    }

    /** Requests a blank line before the next content. */
    void paragraphBreak() {
        requestBreaks(2);
    }

    /** The text so far, without trailing whitespace on any line and without leading/trailing blank lines. */
    String result() {
        String text = sb.toString().replaceAll("[ \\t]+\\n", "\n").stripTrailing();
        int start = 0;
        while (start < text.length() && text.charAt(start) == '\n') {
            start++;
        }
        return text.substring(start);
    }

    private void requestBreaks(int count) {
        if (sb.isEmpty() && lineStart) {
            return;
        }
        pendingBreaks = Math.max(pendingBreaks, count);
        pendingSpace = false;
    }

    private void flushBreaks() {
        if (pendingBreaks > 0 && !sb.isEmpty()) {
            sb.append("\n".repeat(pendingBreaks));
            lineStart = true;
        }
        pendingBreaks = 0;
    }
}
