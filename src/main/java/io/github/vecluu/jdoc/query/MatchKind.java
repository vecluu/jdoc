package io.github.vecluu.jdoc.query;

/** How a name matched a {@link NamePattern}, best first. */
public enum MatchKind {
    /** The name equals the pattern, ignoring case. */
    EXACT,
    /** The name starts with the pattern, ignoring case. */
    PREFIX,
    /** The pattern matches the starts of the name's camel-case words ({@code HSR}, {@code HSRequest}). */
    CAMEL_HUMPS,
    /** The name contains the pattern, ignoring case. */
    SUBSTRING
}
