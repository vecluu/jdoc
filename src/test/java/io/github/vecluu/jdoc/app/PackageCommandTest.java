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

class PackageCommandTest {

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
    void showsPackageDocThenTypes() {
        CommandResult result = new PackageCommand(index.context()).run("com.acme", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(lines(result)).containsExactly(
                "com.acme  package  (shapes 1.0)",
                "Shapes for tests.",
                "",
                "@since 1.0",
                "",
                "abstract class Base          Common base of the shapes.",
                "class Circle                 A circle.",
                "static class Circle.Builder  Builds circles.",
                "final class Lonely           Nobody extends this.",
                "interface Shape              A shape with an area.",
                "class Square                 A unit square.");
    }

    @Test
    void packageWithoutDocListsTypesOnly() {
        CommandResult result = new PackageCommand(index.context(ResolutionStatus.UNRESOLVED, List.of()))
                .run("java.time", Optional.empty());

        assertThat(lines(result)).containsExactly(
                "java.time  package  (jdk 25)",
                "final class Duration  A time-based amount of time, such as '34.5 seconds'.");
    }

    @Test
    void unknownPackageIsNotFoundWithSuggestions() {
        CommandResult result = new PackageCommand(index.context()).run("acme", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.NOT_FOUND);
        assertThat(lines(result)).containsExactly("no package named acme on this classpath", "did you mean:",
                "  com.acme");
    }
}
