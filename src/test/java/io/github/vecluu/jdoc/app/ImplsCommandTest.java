package io.github.vecluu.jdoc.app;

import static io.github.vecluu.jdoc.app.CommandTestSupport.lines;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImplsCommandTest {

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

    private CommandResult run(String type) {
        return new ImplsCommand(index.context()).run(type, Optional.empty());
    }

    @Test
    void listsDirectThenIndirectSubtypesWithArtifact() {
        CommandResult result = run("Shape");

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(lines(result)).containsExactly(
                "com.acme.Shape  public interface  (shapes 1.0)",
                "com.acme.Base    shapes 1.0",
                "com.acme.Circle  shapes 1.0  indirect",
                "com.acme.Square  shapes 1.0  indirect");
    }

    @Test
    void noSubtypesIsNotFound() {
        CommandResult result = run("com.acme.Lonely");

        assertThat(result.exitCode()).isEqualTo(ExitCode.NOT_FOUND);
        assertThat(lines(result)).containsExactly("com.acme.Lonely  public final class  (shapes 1.0)",
                ImplsCommand.NONE);
    }

    @Test
    void ambiguousAndUnknownTypes() {
        assertThat(run("Duration").exitCode()).isEqualTo(ExitCode.AMBIGUOUS);
        assertThat(run("Nope").exitCode()).isEqualTo(ExitCode.NOT_FOUND);
    }
}
