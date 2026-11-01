package io.github.vecluu.jdoc.app;

/** Process exit codes, so callers can react without parsing text. */
public enum ExitCode {
    /** The thing asked for was found. */
    FOUND(0),
    /** Nothing matched. */
    NOT_FOUND(1),
    /** Several types matched a name; candidates are printed. */
    AMBIGUOUS(2),
    /** Usage error, failed resolution with no fallback, or an unexpected failure. */
    ERROR(3);

    private final int code;

    ExitCode(int code) {
        this.code = code;
    }

    /** The numeric process exit code. */
    public int code() {
        return code;
    }
}
