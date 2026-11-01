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

/**
 * The jdoc command line, testable in-process: parses the arguments, runs the command and returns the exit code
 * (it never calls {@link System#exit}).
 *
 * <p>Help and version print on stdout with exit code 0 and never create the application. Usage errors, expected
 * failures ({@link JdocException}, e.g. no {@code pom.xml} or a failed resolution without fallback) and unexpected
 * exceptions print one {@code error: } line on stderr and exit with 3.
 */
public final class JdocCli {

    private final Supplier<Application> applications;
    private final PrintWriter out;
    private final PrintWriter err;
    private final Paginator paginator;

    /**
     * @param applications creates the application when a command runs (not for help, version or usage errors)
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
        try {
            CliRequest request = new CommandLineParser().parse(arguments);
            if (request instanceof CliRequest.Run(JdocCommand command, Invocation invocation)) {
                return run(command, invocation, arguments);
            }
            out.println(request instanceof CliRequest.Help(var command)
                    ? command.map(HelpText::of).orElseGet(HelpText::overview)
                    : ManifestVersion.text());
            return ExitCode.FOUND.code();
        } catch (UsageException e) {
            new FailureReporter(err).usage(e);
            return ExitCode.ERROR.code();
        } finally {
            out.flush();
            err.flush();
        }
    }

    private int run(JdocCommand command, Invocation invocation, List<String> arguments) {
        CommandRunner runner = new CommandRunner(applications, new ResultPrinter(paginator, out, err),
                CommandLineText.of(arguments));
        try {
            return runner.run(invocation.page(), application -> command.call(application, invocation));
        } catch (RuntimeException e) {
            new FailureReporter(err).failure(e);
            return ExitCode.ERROR.code();
        }
    }
}
