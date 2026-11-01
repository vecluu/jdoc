package io.github.vecluu.jdoc.app;

import static io.github.vecluu.jdoc.app.CommandTestSupport.lines;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceCommandTest {

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
        return new SourceCommand(index.context()).run(reference, Optional.empty());
    }

    @Test
    void showsTheMemberWithItsJavadocAndBody() {
        CommandResult result = run("java.util.Optional#orElseThrow()");

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(lines(result)).containsExactly(
                "java.util.Optional<T>  public final class  (jdk 25)",
                "/**",
                " * If a value is present, returns the value, otherwise throws {@code NoSuchElementException}.",
                " *",
                " * @return the non-{@code null} value described by this {@code Optional}",
                " * @throws NoSuchElementException if no value is present",
                " * @since 10",
                " */",
                "public T orElseThrow() {",
                "    if (value == null) {",
                "        throw new NoSuchElementException(\"No value present\");",
                "    }",
                "    return value;",
                "}");
    }

    @Test
    void marksInheritedMembers() {
        assertThat(lines(run("Circle#compareTo"))).element(1).isEqualTo("// inherited from com.acme.Base");
    }

    @Test
    void implicitMemberHasNoSourceAndExitsWithOne() {
        CommandResult result = run("com.acme.Circle.Builder#Builder");

        assertThat(result.exitCode()).isEqualTo(ExitCode.NOT_FOUND);
        assertThat(lines(result)).element(1).isEqualTo("Builder()");
        assertThat(lines(result)).element(2).asString().startsWith("// no source: ");
    }

    @Test
    void notFoundMember() {
        assertThat(run("Optional#nothing").exitCode()).isEqualTo(ExitCode.NOT_FOUND);
    }

    @Test
    void rendersADocAsAJavadocComment() {
        assertThat(SourceCommand.comment(new RenderedDoc("Line one.\n\nLine two.", "Line one.",
                List.of("@return x"), false)))
                .containsExactly("/**", " * Line one.", " *", " * Line two.", " *", " * @return x", " */");
        assertThat(SourceCommand.comment(RenderedDoc.missing())).isEmpty();
    }
}
