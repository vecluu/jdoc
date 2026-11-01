package io.github.vecluu.jdoc.module;

import java.util.Objects;

/**
 * Outcome of a Maven run.
 *
 * @param success  true if Maven exited with 0 (and, for dependency listing, wrote its output file)
 * @param exitCode process exit code, {@code -1} if the process could not be run or timed out
 * @param output   captured stdout and stderr (never shown to the user)
 */
public record MavenResult(boolean success, int exitCode, String output) {

    public MavenResult {
        output = Objects.requireNonNullElse(output, "");
    }

    /** A failure that happened before or around the process (not started, timed out, no output file). */
    public static MavenResult failed(String reason) {
        return new MavenResult(false, -1, reason);
    }

    /** One line describing the failure: the first {@code [ERROR]} line, else the last non-blank output line. */
    public String summary() {
        String lastLine = "";
        for (String line : output.split("\\R")) {
            String stripped = line.strip();
            if (stripped.startsWith("[ERROR]")) {
                return stripped.substring("[ERROR]".length()).strip();
            }
            if (!stripped.isEmpty()) {
                lastLine = stripped;
            }
        }
        return lastLine.isEmpty() ? "exit code " + exitCode : lastLine;
    }
}
