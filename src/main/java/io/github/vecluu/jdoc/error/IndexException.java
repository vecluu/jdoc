package io.github.vecluu.jdoc.error;

/** The index database or its lock failed (SQL errors, schema problems, lock I/O). */
public class IndexException extends JdocException {

    public IndexException(String message) {
        super(message);
    }

    public IndexException(String message, Throwable cause) {
        super(message, cause);
    }
}
