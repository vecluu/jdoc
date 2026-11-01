package io.github.vecluu.jdoc.app;

import static io.github.vecluu.jdoc.app.CommandTestSupport.lines;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ResolutionStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FindCommandTest {

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

    @Test
    void listsMatchingTypesAndMembersWithArtifacts() {
        CommandResult result = new FindCommand(index.context()).run("orElseThrow", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(lines(result)).containsExactly(
                "2 matches for orElseThrow",
                "java.util.Optional#orElseThrow()          jdk 25",
                "java.util.Optional#orElseThrow(Supplier)  jdk 25");
    }

    @Test
    void prefixFindsTypes() {
        assertThat(lines(new FindCommand(index.context()).run("Lon", Optional.empty())))
                .containsExactly("1 match for Lon", "com.acme.Lonely  shapes 1.0");
    }

    @Test
    void nothingMatchingIsNotFound() {
        CommandResult result = new FindCommand(index.context(ResolutionStatus.UNRESOLVED, List.of()))
                .run("zzz", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.NOT_FOUND);
        assertThat(lines(result)).containsExactly("no types or members match zzz  [version unresolved]");
    }

    @Test
    void blankPatternIsAnErrorWithoutOpeningTheModule() {
        FakeModuleContext context = index.context();

        CommandResult result = new FindCommand(context).run("  ", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.ERROR);
        assertThat(result.errorOutput()).containsExactly("error: empty find pattern");
        assertThat(context.froms).isEmpty();
    }
}
