package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.app.CommandResult;
import io.github.vecluu.jdoc.app.ExitCode;
import io.github.vecluu.jdoc.error.ConfigurationException;
import io.github.vecluu.jdoc.error.ResolutionException;
import io.github.vecluu.jdoc.text.Paginator;
import io.github.vecluu.jdoc.text.TextResult;
import io.github.vecluu.jdoc.text.TokenBudget;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JdocCliTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private int applicationsCreated;
    private FakeApplication application;

    private int run(Supplier<CommandResult> answer, String... args) {
        return run(new Paginator(), answer, args);
    }

    private int run(Paginator paginator, Supplier<CommandResult> answer, String... args) {
        application = new FakeApplication(answer);
        JdocCli cli = new JdocCli(() -> {
            applicationsCreated++;
            return application;
        }, new PrintWriter(out), new PrintWriter(err), paginator);
        return cli.execute(args);
    }

    private static Supplier<CommandResult> found(String... lines) {
        return () -> CommandResult.found(TextResult.ofLines(lines[0], List.of(lines).subList(1, lines.length)));
    }

    @Test
    void passesArgumentsToTheApplicationAndPrintsTheResult() {
        int code = run(found("header", "body"), "class", "java.util.Optional");

        assertThat(code).isZero();
        assertThat(application.calls).containsExactly("class java.util.Optional");
        assertThat(application.closed).isTrue();
        assertThat(out.toString()).isEqualTo("header\nbody\n");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void everySubcommandReachesItsService() {
        String[][] commands = {
                {"find", "HSR"}, {"class", "Optional", "--from", "src/A.java"}, {"method", "Optional#of(T)"},
                {"source", "Optional#get"}, {"impls", "List"}, {"member", "of"}, {"package", "java.util"},
                {"index"}, {"index", "--force"}};
        List<String> calls = new ArrayList<>();
        for (String[] command : commands) {
            assertThat(run(found("h"), command)).isZero();
            calls.addAll(application.calls);
        }
        assertThat(calls).containsExactly("find HSR", "class Optional from=src/A.java", "method Optional#of(T)",
                "source Optional#get", "impls List", "member of", "package java.util", "index force=false",
                "index force=true");
    }

    @Test
    void exitCodesComeFromTheResult() {
        assertThat(run(() -> CommandResult.notFound(TextResult.ofLines("none", List.of())), "class", "X"))
                .isEqualTo(1);
        assertThat(run(() -> CommandResult.ambiguous(TextResult.ofLines("2 types", List.of())), "class", "X"))
                .isEqualTo(2);
    }

    @Test
    void errorResultPrintsOneLineOnStderrOnly() {
        int code = run(() -> CommandResult.error("invalid type name"), "class", "?");

        assertThat(code).isEqualTo(3);
        assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).isEqualTo("error: invalid type name\n");
    }

    @Test
    void warningsGoToStderrAndTheResultToStdout() {
        int code = run(() -> CommandResult.found(TextResult.ofLines("h", List.of()))
                .withWarnings(List.of("warning: mvn failed, using classpath resolved at 2026-10-01 12:00")),
                "class", "X");

        assertThat(code).isZero();
        assertThat(err.toString()).isEqualTo("warning: mvn failed, using classpath resolved at 2026-10-01 12:00\n");
        assertThat(out.toString()).isEqualTo("h\n");
    }

    @Test
    void paginatesWithTheOriginalCommandLineInTheFooter() {
        Supplier<CommandResult> longResult = () -> CommandResult.found(TextResult.ofLines("header",
                IntStream.range(0, 30).mapToObj(i -> "line " + i).toList()));
        Paginator small = new Paginator(new TokenBudget(30));

        assertThat(run(small, longResult, "method", "--from", "a b/X.java", "Optional#of(T)")).isZero();
        List<String> first = out.toString().lines().toList();
        assertThat(first.getFirst()).isEqualTo("header");
        assertThat(first.getLast()).matches("page 1/\\d+ · next: jdoc method --from 'a b/X.java' "
                + "'Optional#of\\(T\\)' --page 2");

        out.getBuffer().setLength(0);
        assertThat(run(small, longResult, "method", "--from", "a b/X.java", "Optional#of(T)", "--page", "2"))
                .isZero();
        List<String> second = out.toString().lines().toList();
        assertThat(second.getFirst()).isEqualTo("header");
        assertThat(second.get(1)).isNotEqualTo(first.get(1));
        assertThat(second.getLast()).startsWith("page 2/");
    }

    @Test
    void pagePastTheEndIsNotFound() {
        int code = run(found("h", "b"), "class", "X", "--page", "3");

        assertThat(code).isEqualTo(1);
        assertThat(out.toString()).isEqualTo("page 3 does not exist: this result has 1 page\n");
    }

    @Test
    void helpAndVersionDoNotCreateTheApplication() {
        assertThat(run(found("h"), "--help")).isZero();
        assertThat(out.toString()).contains("Usage: jdoc", "class", "method", "source", "impls", "member",
                "package", "index", "find", "Exit codes: 0 found, 1 not found, 2 ambiguous, 3 error.");
        out.getBuffer().setLength(0);
        assertThat(run(found("h"), "class", "--help")).isZero();
        assertThat(out.toString()).contains("Usage: jdoc class", "--from", "--page", "<type>");
        out.getBuffer().setLength(0);
        assertThat(run(found("h"), "--version")).isZero();
        assertThat(out.toString()).startsWith("jdoc ");
        assertThat(applicationsCreated).isZero();
    }

    @Test
    void usageErrorsExitWithThreeAndAMessageOnStderr() {
        assertThat(run(found("h"), "class")).isEqualTo(3);
        assertThat(err.toString()).startsWith("error: Missing required parameter: '<type>'")
                .contains("(see jdoc class --help)");
        err.getBuffer().setLength(0);
        assertThat(run(found("h"), "frobnicate")).isEqualTo(3);
        assertThat(err.toString()).startsWith("error: Unmatched argument");
        err.getBuffer().setLength(0);
        assertThat(run(found("h"), "class", "X", "--page", "0")).isEqualTo(3);
        assertThat(err.toString()).contains("--page must be at least 1");
        err.getBuffer().setLength(0);
        assertThat(run(found("h"), "class", "X", "--page", "two")).isEqualTo(3);
        err.getBuffer().setLength(0);
        assertThat(run(found("h"))).isEqualTo(3);
        assertThat(err.toString()).startsWith("error: Missing command");
        assertThat(out.toString()).isEmpty();
        assertThat(applicationsCreated).isZero();
    }

    @Test
    void jdocExceptionsBecomeOneErrorLine() {
        int code = run(() -> {
            throw new ResolutionException("no pom.xml found above /tmp/x\nsecond line");
        }, "class", "X");

        assertThat(code).isEqualTo(ExitCode.ERROR.code());
        assertThat(err.toString()).isEqualTo("error: no pom.xml found above /tmp/x second line\n");
        assertThat(out.toString()).isEmpty();
        assertThat(application.closed).isTrue();
    }

    @Test
    void unexpectedExceptionsBecomeOneErrorLine() {
        int code = run(() -> {
            throw new IllegalStateException("bug");
        }, "find", "x");

        assertThat(code).isEqualTo(3);
        assertThat(err.toString()).isEqualTo("error: unexpected IllegalStateException: bug\n");
    }

    @Test
    void failureToCreateTheApplicationIsAnError() {
        JdocCli cli = new JdocCli(() -> {
            throw new ConfigurationException("not a JDK: /opt/jre");
        }, new PrintWriter(out), new PrintWriter(err), new Paginator());

        assertThat(cli.execute("class", "X")).isEqualTo(3);
        assertThat(err.toString()).isEqualTo("error: not a JDK: /opt/jre\n");
    }

    @Test
    void missingCommandPointsAtHelpOnce() {
        assertThat(run(found("h"))).isEqualTo(3);
        assertThat(err.toString()).isEqualTo("error: Missing command, e.g. jdoc class <type> (see jdoc --help)\n");
    }

    @Test
    void helpIsPrintedOnStdoutAsWritten() {
        assertThat(run(found("h"), "index", "--help")).isZero();
        assertThat(out.toString()).isEqualTo(HelpText.of(JdocCommand.INDEX) + "\n");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void versionOutsideTheJarIsADevelopmentBuild() {
        assertThat(run(found("h"), "class", "-V")).isZero();
        assertThat(out.toString()).isEqualTo("jdoc " + ManifestVersion.UNKNOWN + "\n");
    }
}
