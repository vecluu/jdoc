package io.github.vecluu.jdoc.decompile;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

/**
 * Routes Vineflower's messages to slf4j at debug level (never stdout) and remembers whether a whole class failed
 * to decompile, which Vineflower otherwise only reports as a comment in the output.
 */
@Slf4j
final class Slf4jFernflowerLogger extends IFernflowerLogger {

    private static final String CLASS_FAILURE = "couldn't be fully decompiled";

    private volatile Throwable classFailure;

    /** The error of a class that could not be decompiled at all, if any. */
    Optional<Throwable> classFailure() {
        return Optional.ofNullable(classFailure);
    }

    @Override
    public void writeMessage(String message, Severity severity) {
        log.debug("Vineflower {}: {}", severity, message);
    }

    @Override
    public void writeMessage(String message, Severity severity, Throwable t) {
        log.debug("Vineflower {}: {}", severity, message, t);
        if (severity == Severity.ERROR && message != null && message.contains(CLASS_FAILURE) && classFailure == null) {
            classFailure = t;
        }
    }
}
