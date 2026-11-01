package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.text.TextResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one command: the exit code, the text for stdout (paginated by the caller) and lines for stderr
 * (warnings such as a stale classpath, or the one-line error of exit code 3).
 *
 * @param exitCode    how the command ended
 * @param output      the result text; empty for errors
 * @param errorOutput lines for stderr, in order
 */
public record CommandResult(ExitCode exitCode, Optional<TextResult> output, List<String> errorOutput) {

    public CommandResult {
        Objects.requireNonNull(exitCode, "exit code");
        output = output == null ? Optional.empty() : output;
        errorOutput = errorOutput == null ? List.of() : List.copyOf(errorOutput);
    }

    /** A result with text and no stderr lines. */
    public static CommandResult of(ExitCode exitCode, TextResult output) {
        return new CommandResult(exitCode, Optional.of(output), List.of());
    }

    /** Exit code 0 with this text. */
    public static CommandResult found(TextResult output) {
        return of(ExitCode.FOUND, output);
    }

    /** Exit code 1 with this text. */
    public static CommandResult notFound(TextResult output) {
        return of(ExitCode.NOT_FOUND, output);
    }

    /** Exit code 2 with this text (the candidates). */
    public static CommandResult ambiguous(TextResult output) {
        return of(ExitCode.AMBIGUOUS, output);
    }

    /** Exit code 3 with one error line on stderr and nothing on stdout. */
    public static CommandResult error(String message) {
        return new CommandResult(ExitCode.ERROR, Optional.empty(), List.of("error: " + message));
    }

    /** This result with the given lines printed on stderr before its own. */
    public CommandResult withWarnings(List<String> warnings) {
        if (warnings.isEmpty()) {
            return this;
        }
        List<String> lines = new ArrayList<>(warnings);
        lines.addAll(errorOutput);
        return new CommandResult(exitCode, output, lines);
    }
}
