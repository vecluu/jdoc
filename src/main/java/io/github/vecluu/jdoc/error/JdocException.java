package io.github.vecluu.jdoc.error;

/**
 * Base of all jdoc failures. The message is a concise, user-facing line (the CLI prints it after {@code error: }).
 * Expected outcomes such as "not found" or "ambiguous" are values, never exceptions.
 */
public class JdocException extends RuntimeException {

    public JdocException(String message) {
        super(message);
    }

    public JdocException(String message, Throwable cause) {
        super(message, cause);
    }
}
