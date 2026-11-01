package io.github.vecluu.jdoc.app;

import static io.github.vecluu.jdoc.app.CommandTestSupport.lines;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MethodCommandTest {

    @TempDir
    static Path tmp;
    static AppIndex index;

    @BeforeAll
    static void index() {
        index = AppIndex.standard(tmp);
    }

    @AfterAll
    static void close() {
        index.close();
    }

    private CommandResult run(String reference) {
        return new MethodCommand(index.context()).run(reference, Optional.empty());
    }

    @Test
    void rendersTheFullDocOfOneOverload() {
        CommandResult result = run("java.util.Optional#orElseThrow()");

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(lines(result)).containsExactly(
                "java.util.Optional<T>  public final class  (jdk 25)",
                "T orElseThrow()",
                "If a value is present, returns the value, otherwise throws `NoSuchElementException`.",
                "",
                "@return the non-`null` value described by this `Optional`",
                "@throws NoSuchElementException if no value is present",
                "@since 10");
    }

    @Test
    void rendersEveryOverloadSeparatedByABlankLine() {
        CommandResult result = run("Optional#orElseThrow");

        assertThat(lines(result)).containsSubsequence("T orElseThrow()", "@since 10", "",
                "<X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X",
                "@throws X if no value is present");
        assertThat(result.output().orElseThrow().blocks()).hasSize(3);
    }

    @Test
    void marksInheritedMembersAndInheritedDocs() {
        assertThat(lines(run("com.acme.Circle#compareTo"))).contains(
                "int compareTo(Base other)  [inherited from com.acme.Base]", "@param other the other shape");
        assertThat(lines(run("com.acme.Circle#area"))).containsSubsequence(
                "double area()  [doc from com.acme.Shape]", "Computes the area.", "",
                "@return the area, never negative");
    }

    @Test
    void undocumentedMemberSaysSo() {
        assertThat(lines(run("Circle.Builder#Builder"))).containsExactly(
                "com.acme.Circle.Builder  public static class  (shapes 1.0)", "Builder()", "(no documentation)");
    }

    @Test
    void unknownMemberIsNotFoundWithSuggestions() {
        CommandResult result = run("Optional#orElseThrow(int)");

        assertThat(result.exitCode()).isEqualTo(ExitCode.NOT_FOUND);
        assertThat(lines(result)).containsExactly(
                "java.util.Optional<T>  public final class  (jdk 25)",
                "no member Optional#orElseThrow(int) in java.util.Optional",
                "did you mean:",
                "  java.util.Optional#orElseThrow()",
                "  java.util.Optional#orElseThrow(Supplier)");
    }

    @Test
    void ambiguousTypePartListsCandidates() {
        CommandResult result = run("Duration#toString");

        assertThat(result.exitCode()).isEqualTo(ExitCode.AMBIGUOUS);
        assertThat(lines(result)).last().isEqualTo("Re-run with a fully qualified name.");
    }

    @Test
    void missingTypeIsNotFound() {
        assertThat(run("Nothing#x").exitCode()).isEqualTo(ExitCode.NOT_FOUND);
    }

    @Test
    void malformedReferenceIsAnError() {
        CommandResult result = run("Optional#");

        assertThat(result.exitCode()).isEqualTo(ExitCode.ERROR);
        assertThat(result.errorOutput()).singleElement().asString()
                .startsWith("error: invalid member reference 'Optional#'");
    }
}
