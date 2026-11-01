package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.text.TextResult;
import java.util.List;

/** Small helpers for asserting on {@link CommandResult}s. */
final class CommandTestSupport {

    private CommandTestSupport() {
    }

    /** All output lines (header first); fails if the result has no output. */
    static List<String> lines(CommandResult result) {
        return result.output().map(TextResult::allLines).orElseThrow(() -> new AssertionError("no output: " + result));
    }
}
