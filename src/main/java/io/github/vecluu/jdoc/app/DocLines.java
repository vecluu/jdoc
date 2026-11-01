package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.ArrayList;
import java.util.List;

/** Rendered Javadoc as output lines: the description, a blank line, then one line per block tag. */
final class DocLines {

    private DocLines() {
    }

    /** The lines of a doc; empty if the doc has neither description nor tags. */
    static List<String> of(RenderedDoc doc) {
        List<String> lines = new ArrayList<>(split(doc.description()));
        if (!lines.isEmpty() && !doc.blockTags().isEmpty()) {
            lines.add("");
        }
        doc.blockTags().forEach(tag -> lines.addAll(split(tag)));
        return lines;
    }

    /** Splits code into lines, keeping indentation; drops trailing whitespace and outer blank lines. */
    static List<String> verbatim(String text) {
        List<String> lines = new ArrayList<>((text == null ? "" : text).lines().map(String::stripTrailing).toList());
        while (!lines.isEmpty() && lines.getFirst().isEmpty()) {
            lines.removeFirst();
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }

    /** Splits text into lines, dropping trailing whitespace and outer blank lines. */
    static List<String> split(String text) {
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty()) {
            return List.of();
        }
        return stripped.lines().map(String::stripTrailing).toList();
    }
}
