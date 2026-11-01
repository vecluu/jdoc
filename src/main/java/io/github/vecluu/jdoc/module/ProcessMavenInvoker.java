package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.config.JdocEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs Maven as a child process. stdout and stderr are merged and captured (logged at debug only); stdin is closed;
 * {@code JAVA_HOME} is set to the environment's JDK. A run that exceeds the timeout is killed.
 */
@Slf4j
public final class ProcessMavenInvoker implements MavenInvoker {

    /** Default time limit for one Maven run. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final String executable;
    private final Path javaHome;
    private final Duration timeout;

    /**
     * @param executable Maven command: bare name looked up on {@code PATH}, or an absolute path
     * @param javaHome   JDK passed to Maven as {@code JAVA_HOME}
     * @param timeout    time limit per run
     */
    public ProcessMavenInvoker(String executable, Path javaHome, Duration timeout) {
        this.executable = Objects.requireNonNull(executable, "executable must not be null");
        this.javaHome = Objects.requireNonNull(javaHome, "java home must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    /** An invoker using the environment's Maven executable and JDK with {@link #DEFAULT_TIMEOUT}. */
    public static ProcessMavenInvoker from(JdocEnvironment environment) {
        return new ProcessMavenInvoker(environment.mavenExecutable(), environment.javaHome(), DEFAULT_TIMEOUT);
    }

    @Override
    public MavenResult run(MavenCommand command) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(executable);
        commandLine.addAll(command.arguments());
        log.debug("running {} in {}", commandLine, command.workingDirectory());
        ProcessBuilder builder = new ProcessBuilder(commandLine)
                .directory(command.workingDirectory().toFile())
                .redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", javaHome.toString());
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return MavenResult.failed("cannot run " + executable + ": " + e.getMessage());
        }
        OutputCollector collector = new OutputCollector(process.getInputStream());
        Thread reader = Thread.ofVirtual().name("mvn-output").start(collector);
        try {
            process.getOutputStream().close();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return MavenResult.failed(executable + " timed out after " + timeout.toSeconds() + " s");
            }
            reader.join(Duration.ofSeconds(5));
            String output = collector.output();
            log.debug("mvn exited with {}:\n{}", process.exitValue(), output);
            return new MavenResult(process.exitValue() == 0, process.exitValue(), output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return MavenResult.failed(executable + " was interrupted");
        } catch (IOException e) {
            process.destroyForcibly();
            return MavenResult.failed("cannot talk to " + executable + ": " + e.getMessage());
        }
    }

    /** Drains the process output so it never blocks on a full pipe. */
    private static final class OutputCollector implements Runnable {
        private final InputStream in;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        OutputCollector(InputStream in) {
            this.in = in;
        }

        @Override
        public void run() {
            try (in) {
                in.transferTo(buffer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String output() {
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }
    }
}
