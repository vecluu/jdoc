package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.error.JdocException;
import java.io.PrintWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a failed command into one {@code error: } line on stderr; the stack trace is logged at debug level only.
 * A class of its own so that the logger is initialised only when something fails, not on every call.
 */
@Slf4j
@RequiredArgsConstructor
final class FailureReporter {

    private final PrintWriter err;

    /** {@code error: <message> (see jdoc <command> --help)}. */
    void usage(UsageException exception) {
        err.println("error: " + oneLine(exception.getMessage()) + " (see " + exception.helpHint() + ")");
    }

    /** {@code error: <message>} for a {@link JdocException}, {@code error: unexpected <Type>: <message>} otherwise. */
    void failure(RuntimeException exception) {
        if (exception instanceof JdocException) {
            log.debug("command failed", exception);
            err.println("error: " + oneLine(exception.getMessage()));
        } else {
            log.debug("unexpected failure", exception);
            err.println("error: unexpected " + exception.getClass().getSimpleName() + ": "
                    + oneLine(String.valueOf(exception.getMessage())));
        }
    }

    private static String oneLine(String message) {
        return message == null ? "" : message.strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
