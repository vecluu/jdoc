package io.github.vecluu.jdoc.model;

/**
 * A half-open range {@code [start, end)} of character offsets into a source text.
 *
 * @param start first character offset, inclusive
 * @param end   last character offset, exclusive
 */
public record SourceRange(int start, int end) {
    public SourceRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid source range [" + start + ", " + end + ")");
        }
    }

    /** Number of characters in the range. */
    public int length() {
        return end - start;
    }

    /** True if the other range lies completely inside this one. */
    public boolean contains(SourceRange other) {
        return other.start >= start && other.end <= end;
    }

    /**
     * The text covered by this range.
     *
     * @throws IllegalArgumentException if the range exceeds the text
     */
    public String slice(CharSequence text) {
        if (end > text.length()) {
            throw new IllegalArgumentException("range " + this + " exceeds text length " + text.length());
        }
        return text.subSequence(start, end).toString();
    }
}
