package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Keeps {@code scripts/bench-startup.sh} working: one measured run per command and per "first call after adding a
 * dependency" scenario on the fat jar, with the script's temporary project, fake {@code mvn} and {@code JDOC_CACHE}
 * inside a test directory.
 */
class BenchStartupScriptIT {

    private static final List<String> COMMANDS = List.of("--version", "class java.util.Optional",
            "method java.util.Optional#orElseThrow", "find HashMap", "member orElseThrow",
            "source java.util.Optional#orElseThrow",
            "new-dep sources: class bench.fresh.Ledger", "new-dep javadoc: class bench.fresh.Ledger",
            "new-dep binary: source bench.fresh.Ledgers#of");

    @TempDir
    Path tmp;

    @Test
    void reportsAMedianForEveryCommand() throws Exception {
        Path jar = fatJar();
        Path script = Path.of(System.getProperty("user.dir"), "scripts", "bench-startup.sh");
        assumeTrue(Files.isRegularFile(jar), "fat jar not built: " + jar);
        assumeTrue(Files.isRegularFile(script), "script not found: " + script);
        assumeTrue(onPath("bash"), "no bash on PATH");

        ProcessBuilder builder = new ProcessBuilder("bash", script.toString(), jar.toString())
                .redirectErrorStream(true);
        builder.environment().put("BENCH_RUNS", "1");
        builder.environment().put("BENCH_WARMUP", "0");
        builder.environment().put("TMPDIR", tmp.toString());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        builder.environment().remove("BENCH_COMMANDS");
        builder.environment().remove("BENCH_JVM_FLAGS");
        builder.environment().remove("BENCH_JAVA");
        Process process = builder.start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("finished").isTrue();

        assertThat(process.exitValue()).as(output).isZero();
        List<String> rows = output.lines().filter(line -> line.matches("\\s*\\d+\\s+\\d+\\s+\\d+\\s+.+")).toList();
        assertThat(rows).as(output).hasSize(COMMANDS.size());
        for (int i = 0; i < COMMANDS.size(); i++) {
            assertThat(rows.get(i)).endsWith("  " + COMMANDS.get(i));
        }
        try (var leftovers = Files.list(tmp)) {
            assertThat(leftovers).as("temporary directory removed").isEmpty();
        }
    }

    private static boolean onPath(String command) {
        String path = System.getenv("PATH");
        return path != null && java.util.Arrays.stream(path.split(java.io.File.pathSeparator))
                .anyMatch(directory -> Files.isExecutable(Path.of(directory, command)));
    }

    private static Path fatJar() throws URISyntaxException {
        Path testClasses = Path.of(BenchStartupScriptIT.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        return testClasses.getParent().resolve("jdoc.jar");
    }
}
