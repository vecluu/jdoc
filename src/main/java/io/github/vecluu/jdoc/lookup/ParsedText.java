package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.source.ParsedSourceFile;
import java.util.Objects;

/**
 * A source file's text together with its parse result; offsets in the parse refer to {@link #text()}.
 *
 * @param text the complete file text
 * @param file the parsed file
 */
public record ParsedText(String text, ParsedSourceFile file) {
    public ParsedText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(file, "file");
    }
}
