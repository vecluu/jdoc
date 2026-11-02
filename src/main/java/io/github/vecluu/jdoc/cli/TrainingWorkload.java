package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.ExitCode;
import io.github.vecluu.jdoc.config.JdocEnvironment;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs a list of jdoc commands one after another in a single JVM, each through a fresh {@link JdocCli} exactly as a
 * separate call would. {@code dist/install.sh} uses it as the training run for the JDK AOT cache, which records the
 * classes of one JVM only.
 *
 * <p>Hidden on purpose: it is reached through the {@value #PROPERTY} system property (the path of a workload file),
 * never through the command line. The workload file holds one command per line, its arguments separated by
 * whitespace; blank lines and lines starting with {@code #} are skipped.
 */
public final class TrainingWorkload {

    /** System property naming the workload file; when set, {@code Main} runs the workload instead of the arguments. */
    public static final String PROPERTY = "jdoc.aotTraining";

    private final Supplier<JdocCli> clis;
    private final PrintWriter err;

    /**
     * @param clis creates the command line for each command
     * @param err  stderr, for one line per failed command
     */
    public TrainingWorkload(Supplier<JdocCli> clis, PrintWriter err) {
        this.clis = clis;
        this.err = err;
    }

    /** The real command line for every command: process environment, real components, output on {@code out}. */
    public static TrainingWorkload forEnvironment(PrintWriter out, PrintWriter err) {
        return new TrainingWorkload(() -> JdocCli.forEnvironment(JdocEnvironment::fromSystem, out, err), err);
    }

    /**
     * Reads a workload file and runs it.
     *
     * @return {@code 0} if no command ended with the error exit code, else the error exit code
     */
    public int runFile(Path workload) {
        String text;
        try {
            text = Files.readString(workload, StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("error: cannot read the training workload " + workload + ": " + e.getMessage());
            err.flush();
            return ExitCode.ERROR.code();
        }
        return run(parse(text));
    }

    /**
     * Runs the commands in order. "Not found" and "ambiguous" are normal answers; only the error exit code counts as
     * a failure, and the remaining commands still run.
     *
     * @return {@code 0} if no command ended with the error exit code, else the error exit code
     */
    public int run(List<List<String>> commands) {
        boolean failed = false;
        for (List<String> command : commands) {
            int code = clis.get().execute(command.toArray(String[]::new));
            if (code == ExitCode.ERROR.code()) {
                err.println("error: training command '" + String.join(" ", command) + "' exited with " + code);
                failed = true;
            }
        }
        err.flush();
        return failed ? ExitCode.ERROR.code() : ExitCode.FOUND.code();
    }

    /** Splits a workload text into commands: one per non-blank line not starting with {@code #}. */
    static List<List<String>> parse(String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> List.of(line.split("\\s+")))
                .toList();
    }
}
