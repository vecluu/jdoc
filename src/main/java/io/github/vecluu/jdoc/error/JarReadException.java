package io.github.vecluu.jdoc.error;

/** A jar, zip or class file could not be read or is corrupt. */
public class JarReadException extends JdocException {

    public JarReadException(String message) {
        super(message);
    }

    public JarReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
