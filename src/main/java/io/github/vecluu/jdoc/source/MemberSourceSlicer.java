package io.github.vecluu.jdoc.source;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.SourceRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cuts a member's text out of its source file by the offsets stored in the index, re-indented so the first line
 * is not misaligned: the slice is extended back to the start of its first line and the indentation common to all
 * lines is removed. Stateless and thread-safe.
 */
public final class MemberSourceSlicer {

    /** The whole member (doc comment, if any, through the end of the body); empty if it has no range. */
    public Optional<String> member(String text, MemberDescriptor member) {
        return member.range().flatMap(range -> slice(text, range));
    }

    /** The doc comment alone, if the member has one. */
    public Optional<String> docComment(String text, MemberDescriptor member) {
        return member.docRange().flatMap(range -> slice(text, range));
    }

    /** The declaration without its doc comment (annotations, signature and body). */
    public Optional<String> declaration(String text, MemberDescriptor member) {
        return member.range().flatMap(range -> {
            int start = member.docRange().map(SourceRange::end).orElse(range.start());
            while (start < range.end() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            return slice(text, new SourceRange(start, range.end()));
        });
    }

    /**
     * The text of a range, with the whitespace before it on its first line restored and common indentation
     * removed; empty if the range exceeds the text.
     */
    public Optional<String> slice(String text, SourceRange range) {
        if (range.end() > text.length()) {
            return Optional.empty();
        }
        int start = range.start();
        while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) {
            start--;
        }
        return Optional.of(dedent(text.substring(start, range.end())));
    }

    private static String dedent(String block) {
        String[] lines = block.replace("\r\n", "\n").split("\n", -1);
        int indent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.isBlank()) {
                indent = Math.min(indent, indentation(line));
            }
        }
        if (indent == Integer.MAX_VALUE) {
            indent = 0;
        }
        List<String> result = new ArrayList<>(lines.length);
        for (String line : lines) {
            result.add(line.isBlank() ? "" : line.substring(indent).stripTrailing());
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
