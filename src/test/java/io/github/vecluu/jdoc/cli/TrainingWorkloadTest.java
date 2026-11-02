package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.app.CommandResult;
import io.github.vecluu.jdoc.text.Paginator;
import io.github.vecluu.jdoc.text.TextResult;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrainingWorkloadTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final List<FakeApplication> applications = new ArrayList<>();

    @TempDir
    Path tmp;

    private TrainingWorkload workload(Supplier<CommandResult> answer) {
        return new TrainingWorkload(() -> new JdocCli(() -> {
            FakeApplication application = new FakeApplication(answer);
            applications.add(application);
            return application;
        }, new PrintWriter(out), new PrintWriter(err), new Paginator()), new PrintWriter(err));
    }

    private static Supplier<CommandResult> found() {
        return () -> CommandResult.found(TextResult.ofLines("header", List.of("body")));
    }

    @Test
    void parsesOneCommandPerLineSkippingBlankAndCommentLines() {
        List<List<String>> commands = TrainingWorkload.parse("""
                # warm the index
                index

                  method   java.util.Optional#orElseThrow
                class java.util.Optional --from src/A.java
                """);

        assertThat(commands).containsExactly(List.of("index"), List.of("method", "java.util.Optional#orElseThrow"),
                List.of("class", "java.util.Optional", "--from", "src/A.java"));
    }

    @Test
    void runsEveryCommandThroughAFreshApplication() {
        int code = workload(found()).run(List.of(List.of("find", "HSR"), List.of("member", "of"),
                List.of("--version")));

        assertThat(code).isZero();
        assertThat(applications).hasSize(2);
        assertThat(applications.get(0).calls).containsExactly("find HSR");
        assertThat(applications.get(1).calls).containsExactly("member of");
        assertThat(applications).allMatch(application -> application.closed);
        assertThat(out.toString()).contains("header\nbody\n").contains("jdoc ");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void notFoundIsANormalAnswer() {
        int code = workload(() -> CommandResult.notFound(TextResult.ofLines("not found", List.of())))
                .run(List.of(List.of("class", "Nope")));

        assertThat(code).isZero();
    }

    @Test
    void anErrorFailsTheWorkloadButTheRestStillRuns() {
        int code = workload(found()).run(List.of(List.of("class"), List.of("member", "of")));

        assertThat(code).isEqualTo(3);
        assertThat(applications).hasSize(1);
        assertThat(err.toString()).contains("error: training command 'class' exited with 3");
    }

    @Test
    void runsAWorkloadFile() throws Exception {
        Path file = Files.writeString(tmp.resolve("workload.txt"), "find HSR\npackage java.util\n");

        int code = workload(found()).runFile(file);

        assertThat(code).isZero();
        assertThat(applications).extracting(application -> application.calls.getFirst())
                .containsExactly("find HSR", "package java.util");
    }

    @Test
    void aMissingWorkloadFileIsAnError() {
        int code = workload(found()).runFile(tmp.resolve("missing.txt"));

        assertThat(code).isEqualTo(3);
        assertThat(err.toString()).startsWith("error: cannot read the training workload ");
        assertThat(applications).isEmpty();
    }
}
