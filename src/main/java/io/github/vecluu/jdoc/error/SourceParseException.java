package io.github.vecluu.jdoc.error;

/** A source file or documentation page could not be parsed. */
public class SourceParseException extends JdocException {

    public SourceParseException(String message) {
        super(message);
    }

    public SourceParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
