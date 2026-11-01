package io.github.vecluu.jdoc;

import io.github.vecluu.jdoc.cli.JdocCli;
import io.github.vecluu.jdoc.config.JdocEnvironment;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Process entry point: configures logging, runs {@link JdocCli} and exits with its code. */
public final class Main {

    private Main() {
    }

    /** Runs jdoc and exits the JVM with the command's exit code. */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Runs jdoc against the process environment without exiting.
     *
     * @return the exit code
     */
    static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        configureLogging();
        PrintWriter out = new PrintWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8));
        PrintWriter err = new PrintWriter(new OutputStreamWriter(stderr, StandardCharsets.UTF_8));
        return JdocCli.forEnvironment(JdocEnvironment::fromSystem, out, err).execute(args);
    }

    /** slf4j-simple on stderr at {@code warn} unless overridden with {@code -Dorg.slf4j.simpleLogger.*}. */
    private static void configureLogging() {
        setIfAbsent("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        setIfAbsent("org.slf4j.simpleLogger.logFile", "System.err");
        setIfAbsent("org.slf4j.simpleLogger.showThreadName", "false");
        setIfAbsent("org.slf4j.simpleLogger.showLogName", "false");
        setIfAbsent("org.slf4j.simpleLogger.levelInBrackets", "false");
    }

    private static void setIfAbsent(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
