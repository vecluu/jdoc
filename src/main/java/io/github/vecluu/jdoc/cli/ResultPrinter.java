package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.CommandResult;
import io.github.vecluu.jdoc.app.ExitCode;
import io.github.vecluu.jdoc.text.PageOutcome;
import io.github.vecluu.jdoc.text.Paginator;
import io.github.vecluu.jdoc.text.TextResult;
import java.io.PrintWriter;
import lombok.RequiredArgsConstructor;

/**
 * Prints a {@link CommandResult}: stderr lines first, then the requested page of the text on stdout. A page past the
 * end prints {@code page 5 does not exist: this result has 3 pages} and exits with code 1.
 */
@RequiredArgsConstructor
final class ResultPrinter {

    private final Paginator paginator;
    private final PrintWriter out;
    private final PrintWriter err;

    /**
     * Prints the result.
     *
     * @param result      the command's result
     * @param page        the requested page (1-based)
     * @param commandLine the command line for the footer
     * @return the process exit code
     */
    int print(CommandResult result, int page, String commandLine) {
        result.errorOutput().forEach(err::println);
        err.flush();
        if (result.output().isEmpty()) {
            return result.exitCode().code();
        }
        TextResult text = result.output().get();
        int exitCode = result.exitCode().code();
        switch (paginator.page(text, page, commandLine)) {
            case PageOutcome.Shown shown -> out.println(shown.page().text());
            case PageOutcome.OutOfRange outOfRange -> {
                out.println(outOfRange.message());
                exitCode = ExitCode.NOT_FOUND.code();
            }
        }
        out.flush();
        return exitCode;
    }
}
