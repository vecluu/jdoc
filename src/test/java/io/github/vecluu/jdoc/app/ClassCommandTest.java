package io.github.vecluu.jdoc.app;

import static io.github.vecluu.jdoc.app.CommandTestSupport.lines;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.indexer.IndexOptions;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassCommandTest {

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
    void showsHeaderFirstParagraphMembersAndInheritedNames() {
        FakeModuleContext context = index.context();

        CommandResult result = new ClassCommand(context).run("java.util.Optional", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(result.errorOutput()).isEmpty();
        assertThat(lines(result)).containsExactly(
                "java.util.Optional<T>  public final class  (jdk 25)",
                "A container object which may or may not contain a non-`null` value. If a value is present,"
                        + " `isPresent()` returns `true`.",
                "",
                "static <T> Optional<T> of(T value)  Returns an `Optional`"
                        + " describing the given non-`null` value.",
                "T orElseThrow()                     If a value is present,"
                        + " returns the value, otherwise throws `NoSuchElementException`.",
                "<X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X  If a value"
                        + " is present, returns the value, otherwise throws an exception produced by the exception"
                        + " supplying function.",
                "boolean equals(Object obj)          Indicates whether some other"
                        + " object is \"equal to\" this `Optional`.",
                "inherited from Object: getClass, hashCode");
        assertThat(context.options).containsExactly(IndexOptions.lookup());
    }

    @Test
    void showsSupertypesNestedTypesAndDeprecation() {
        CommandResult result = new ClassCommand(index.context()).run("Circle", Optional.empty());

        assertThat(lines(result)).first().isEqualTo("com.acme.Circle  public class  (shapes 1.0)");
        assertThat(lines(result).get(1)).isEqualTo("extends com.acme.Base");
        assertThat(lines(result).get(2)).isEqualTo("A circle.");
        assertThat(lines(result)).anySatisfy(line -> assertThat(line).startsWith("double size()")
                .endsWith("[deprecated] Old name of #area()."));
        assertThat(lines(result)).anySatisfy(line -> assertThat(line).startsWith("class Circle.Builder")
                .endsWith("Builds circles."));
        assertThat(lines(result)).contains("inherited from Base: compareTo", "inherited from Object: equals, "
                + "getClass, hashCode");
    }

    @Test
    void ambiguousSimpleNameListsCandidatesWithExitCodeTwo() {
        CommandResult result = new ClassCommand(index.context()).run("Duration", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.AMBIGUOUS);
        assertThat(lines(result)).containsExactly(
                "2 types named Duration on this classpath:",
                "  java.time.Duration           jdk 25",
                "  javax.xml.datatype.Duration  jdk 25",
                "Re-run with a fully qualified name.");
    }

    @Test
    void unknownTypeIsNotFoundWithSuggestions() {
        CommandResult result = new ClassCommand(index.context()).run("optional", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.NOT_FOUND);
        assertThat(lines(result)).containsExactly(
                "no type named optional on this classpath",
                "did you mean:",
                "  java.util.Optional  jdk 25");
    }

    @Test
    void unknownTypeWithoutSuggestions() {
        CommandResult result = new ClassCommand(index.context()).run("com.acme.Nothing", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.NOT_FOUND);
        assertThat(lines(result)).containsExactly("no type named com.acme.Nothing on this classpath");
    }

    @Test
    void unparsableTypeIsAnError() {
        CommandResult result = new ClassCommand(index.context()).run("not a type!", Optional.empty());

        assertThat(result.exitCode()).isEqualTo(ExitCode.ERROR);
        assertThat(result.output()).isEmpty();
        assertThat(result.errorOutput()).singleElement().asString().startsWith("error: invalid type name 'not a type!'");
    }

    @Test
    void passesFromAndWarningsThrough() {
        FakeModuleContext context = index.context(ResolutionStatus.STALE,
                List.of("warning: mvn failed, using classpath resolved at 2026-10-01 12:00"));

        CommandResult result = new ClassCommand(context).run("Lonely", Optional.of(Path.of("src/A.java")));

        assertThat(result.exitCode()).isEqualTo(ExitCode.FOUND);
        assertThat(result.errorOutput()).containsExactly(
                "warning: mvn failed, using classpath resolved at 2026-10-01 12:00");
        assertThat(context.froms).containsExactly(Optional.of(Path.of("src/A.java")));
        assertThat(lines(result).getFirst()).isEqualTo("com.acme.Lonely  public final class  (shapes 1.0)");
    }

    @Test
    void unresolvedVersionsTagLibraryHeadersButNotJdkHeaders() {
        FakeModuleContext context = index.context(ResolutionStatus.UNRESOLVED, List.of());

        assertThat(lines(new ClassCommand(context).run("Lonely", Optional.empty())).getFirst())
                .isEqualTo("com.acme.Lonely  public final class  (shapes 1.0)  [version unresolved]");
        assertThat(lines(new ClassCommand(context).run("java.util.Optional", Optional.empty())).getFirst())
                .isEqualTo("java.util.Optional<T>  public final class  (jdk 25)");
        assertThat(lines(new ClassCommand(context).run("Duration", Optional.empty())).getFirst())
                .isEqualTo("2 types named Duration on this classpath:  [version unresolved]");
    }
}
