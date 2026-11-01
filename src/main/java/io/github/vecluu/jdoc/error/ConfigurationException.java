package io.github.vecluu.jdoc.error;

/** The environment is unusable: e.g. a Java home that is not a JDK, or an invalid cache path. */
public class ConfigurationException extends JdocException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
