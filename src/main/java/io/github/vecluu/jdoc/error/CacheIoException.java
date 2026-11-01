package io.github.vecluu.jdoc.error;

/** Reading or writing a file below the cache directory failed. */
public class CacheIoException extends JdocException {

    public CacheIoException(String message) {
        super(message);
    }

    public CacheIoException(String message, Throwable cause) {
        super(message, cause);
    }
}
