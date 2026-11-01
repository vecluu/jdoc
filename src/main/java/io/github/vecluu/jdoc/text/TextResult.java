package io.github.vecluu.jdoc.text;

import java.util.ArrayList;
import java.util.List;

/**
 * The text of a command result: one header line followed by body blocks.
 *
 * <p>The header names what the result is about (see {@link ResultHeader}) and is repeated on every page when the
 * result is paginated. The body is a list of {@link TextBlock}s; a block is kept on one page where possible.
 *
 * @param header the first line of the result
 * @param blocks the body, in order
 */
public record TextResult(String header, List<TextBlock> blocks) {

    public TextResult {
        Lines.requireSingleLine(header);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /** A result whose body lines are independent (each line is its own block). */
    public static TextResult ofLines(String header, List<String> lines) {
        return builder(header).lines(lines).build();
    }

    /** Starts a builder for a result with the given header line. */
    public static Builder builder(String header) {
        return new Builder(header);
    }

    /** The body lines, flattened, without the header. */
    public List<String> bodyLines() {
        List<String> lines = new ArrayList<>();
        blocks.forEach(block -> lines.addAll(block.lines()));
        return List.copyOf(lines);
    }

    /** All lines: the header followed by the body. */
    public List<String> allLines() {
        List<String> lines = new ArrayList<>();
        lines.add(header);
        lines.addAll(bodyLines());
        return List.copyOf(lines);
    }

    /** The whole result as text, lines joined by {@code \n}, no trailing line break. */
    public String text() {
        return String.join("\n", allLines());
    }

    /** Builder that collects lines and blocks in order. */
    public static final class Builder {
        private final String header;
        private final List<TextBlock> blocks = new ArrayList<>();

        private Builder(String header) {
            this.header = header;
        }

        /** Adds a line as its own block. */
        public Builder line(String line) {
            blocks.add(TextBlock.line(line));
            return this;
        }

        /** Adds an empty line. */
        public Builder blankLine() {
            return line("");
        }

        /** Adds each line as its own block. */
        public Builder lines(List<String> lines) {
            lines.forEach(this::line);
            return this;
        }

        /** Adds lines that should stay on one page together. */
        public Builder block(List<String> lines) {
            blocks.add(new TextBlock(lines));
            return this;
        }

        /** Adds a block. */
        public Builder block(TextBlock block) {
            blocks.add(block);
            return this;
        }

        /** Builds the immutable result. */
        public TextResult build() {
            return new TextResult(header, blocks);
        }
    }
}
