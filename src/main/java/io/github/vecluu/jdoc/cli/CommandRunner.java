package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.Application;
import io.github.vecluu.jdoc.app.CommandResult;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/**
 * Runs one subcommand: creates the application (only now, so {@code --help} and usage errors never open the
 * index), calls it, prints the requested page and closes the application.
 */
@RequiredArgsConstructor
final class CommandRunner {

    private final Supplier<Application> applications;
    private final ResultPrinter printer;
    private final String commandLine;

    /**
     * @param page the requested page
     * @param call the application call of the subcommand
     * @return the process exit code
     */
    int run(int page, Function<Application, CommandResult> call) {
        try (Application application = applications.get()) {
            return printer.print(call.apply(application), page, commandLine);
        }
    }
}
