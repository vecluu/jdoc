package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.Application;
import io.github.vecluu.jdoc.app.ExitCode;
import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.error.JdocException;
import io.github.vecluu.jdoc.text.Paginator;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

/**
 * The jdoc command line, testable in-process: parses the arguments, runs the subcommand and returns the exit code
 * (it never calls {@link System#exit}).
 *
 * <p>Usage errors print picocli's message on stderr and exit with 3. Expected failures ({@link JdocException}, e.g.
 * no {@code pom.xml} or a failed resolution without fallback) and unexpected exceptions print one {@code error: }
 * line on stderr and exit with 3; the stack trace is logged at debug level only.
 */
@Slf4j
public final class JdocCli {

    private final Supplier<Application> applications;
    private final PrintWriter out;
    private final PrintWriter err;
    private final Paginator paginator;

    /**
     * @param applications creates the application when a subcommand runs (not for help, version or usage errors)
     * @param out          stdout
     * @param err          stderr
     * @param paginator    the output cap
     */
    public JdocCli(Supplier<Application> applications, PrintWriter out, PrintWriter err, Paginator paginator) {
        this.applications = applications;
        this.out = out;
        this.err = err;
        this.paginator = paginator;
    }

    /** The real command line: the process environment, the real components, the default output cap. */
    public static JdocCli forEnvironment(Supplier<JdocEnvironment> environment, PrintWriter out, PrintWriter err) {
        return new JdocCli(() -> CompositionRoot.create(environment.get()), out, err, new Paginator());
    }

    /**
     * Runs jdoc.
     *
     * @param args the command-line arguments
     * @return the process exit code
     */
    public int execute(String... args) {
        List<String> arguments = Arrays.asList(args);
        CommandRunner runner = new CommandRunner(applications, new ResultPrinter(paginator, out, err),
                CommandLineText.of(arguments));
        CommandLine commandLine = new CommandLine(new JdocCommand(runner))
                .setOut(out)
                .setErr(err)
                .setParameterExceptionHandler(this::usageError)
                .setExecutionExceptionHandler((exception, line, parsed) -> failure(exception));
        try {
            return commandLine.execute(args);
        } finally {
            out.flush();
            err.flush();
        }
    }

    private int usageError(ParameterException exception, String[] args) {
        String help = exception.getCommandLine().getCommandSpec().qualifiedName() + " --help";
        err.println("error: " + oneLine(exception.getMessage()) + " (see " + help + ")");
        err.flush();
        return ExitCode.ERROR.code();
    }

    private int failure(Exception exception) {
        if (exception instanceof JdocException) {
            log.debug("command failed", exception);
            err.println("error: " + oneLine(exception.getMessage()));
        } else {
            log.debug("unexpected failure", exception);
            err.println("error: unexpected " + exception.getClass().getSimpleName() + ": "
                    + oneLine(String.valueOf(exception.getMessage())));
        }
        err.flush();
        return ExitCode.ERROR.code();
    }

    private static String oneLine(String message) {
        return message == null ? "" : message.strip().replaceAll("\\s*\\R\\s*", " ");
    }
}
