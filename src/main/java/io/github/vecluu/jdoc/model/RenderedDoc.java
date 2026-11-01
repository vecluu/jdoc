package io.github.vecluu.jdoc.model;

import java.util.List;

/**
 * A Javadoc comment rendered to compact plain text.
 *
 * @param description   main description; paragraphs separated by a blank line, {@code <pre>} blocks verbatim
 * @param firstSentence first sentence of the description, one line
 * @param blockTags     one line per block tag, in comment order: {@code @param x ...}, {@code @return ...}, ...
 * @param inheritsDoc   true if the comment is missing or contains an unresolved {@code {@inheritDoc}}
 */
public record RenderedDoc(String description, String firstSentence, List<String> blockTags, boolean inheritsDoc) {

    private static final RenderedDoc MISSING = new RenderedDoc("", "", List.of(), true);

    public RenderedDoc {
        description = description == null ? "" : description;
        firstSentence = firstSentence == null ? "" : firstSentence;
        blockTags = blockTags == null ? List.of() : List.copyOf(blockTags);
    }

    /** The value for a member without any doc comment: empty text, {@code inheritsDoc} true. */
    public static RenderedDoc missing() {
        return MISSING;
    }

    /** True if there is neither description nor block tags. */
    public boolean isEmpty() {
        return description.isBlank() && blockTags.isEmpty();
    }
}
