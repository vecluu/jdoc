package io.github.vecluu.jdoc.module;

import static io.github.vecluu.jdoc.module.PomFixtures.writeFile;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessMavenInvokerTest {

    @TempDir
    Path tmp;

    @Test
    void runsExecutableWithArgumentsInDirectoryAndCapturesOutput() throws Exception {
        Path script = script("""
                echo "args: $*"
                echo "pwd: $(pwd)"
                echo "java: $JAVA_HOME" >&2
                read line && echo "stdin: $line"
                exit 0
                """);
        Path work = Files.createDirectories(tmp.resolve("work dir"));

        MavenResult result = new ProcessMavenInvoker(script.toString(), Path.of("/opt/jdk"), Duration.ofSeconds(10))
                .run(new MavenCommand(work, List.of("-o", "a b")));

        assertThat(result.success()).isTrue();
        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("args: -o a b", "pwd: " + work.toRealPath(), "java: /opt/jdk")
                .doesNotContain("stdin:");
    }

    @Test
    void nonZeroExitIsFailureWithCapturedErrors() throws Exception {
        Path script = script("echo '[ERROR] Could not resolve dependencies' >&2\nexit 1\n");

        MavenResult result = new ProcessMavenInvoker(script.toString(), tmp, Duration.ofSeconds(10))
                .run(new MavenCommand(tmp, List.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.summary()).isEqualTo("Could not resolve dependencies");
    }

    @Test
    void missingExecutableIsFailure() {
        MavenResult result = new ProcessMavenInvoker(tmp.resolve("no-mvn").toString(), tmp, Duration.ofSeconds(10))
                .run(new MavenCommand(tmp, List.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.summary()).startsWith("cannot run");
    }

    @Test
    void timeoutKillsTheProcess() throws Exception {
        Path script = script("exec sleep 30\n");

        long start = System.nanoTime();
        MavenResult result = new ProcessMavenInvoker(script.toString(), tmp, Duration.ofMillis(300))
                .run(new MavenCommand(tmp, List.of()));

        assertThat(result.success()).isFalse();
        assertThat(result.summary()).contains("timed out");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    private Path script(String body) throws Exception {
        Path script = writeFile(tmp.resolve("bin/mvn"), "#!/bin/sh\n" + body);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }
}
