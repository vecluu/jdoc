package io.github.vecluu.jdoc.app;

import static io.github.vecluu.jdoc.app.CommandTestSupport.lines;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemberCommandTest {

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

    private CommandResult run(String name) {
        return new MemberCommand(index.context()).run(name, Optional.empty());
    }

    @Test
    void listsEveryDeclaringTypeAsReference() {
        CommandResult result = run("area");

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(lines(result)).containsExactly(
                "3 members named area",
                "com.acme.Circle#area()  shapes 1.0",
                "com.acme.Shape#area()   shapes 1.0",
                "com.acme.Square#area()  shapes 1.0");
    }

    @Test
    void singleMember() {
        assertThat(lines(run("build"))).first().isEqualTo("1 member named build");
    }

    @Test
    void unknownNameIsNotFound() {
        CommandResult result = run("nothing");

        assertThat(result.exitCode()).isEqualTo(ExitCode.NOT_FOUND);
        assertThat(lines(result)).containsExactly("no member named nothing on this classpath");
    }

    @Test
    void blankNameIsAnError() {
        assertThat(run(" ").exitCode()).isEqualTo(ExitCode.ERROR);
    }
}
