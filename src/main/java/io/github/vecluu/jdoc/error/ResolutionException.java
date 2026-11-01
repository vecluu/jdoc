package io.github.vecluu.jdoc.error;

/** The module or its classpath could not be determined and no fallback was usable. */
public class ResolutionException extends JdocException {

    public ResolutionException(String message) {
        super(message);
    }

    public ResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
