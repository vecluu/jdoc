package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommandLineParserTest {

    private final CommandLineParser parser = new CommandLineParser();

    private CliRequest parse(String... args) {
        return parser.parse(List.of(args));
    }

    private Invocation run(JdocCommand expected, String... args) {
        CliRequest request = parse(args);
        assertThat(request).isInstanceOf(CliRequest.Run.class);
        CliRequest.Run run = (CliRequest.Run) request;
        assertThat(run.command()).isEqualTo(expected);
        return run.invocation();
    }

    private void usageError(String message, String hint, String... args) {
        assertThatThrownBy(() -> parse(args))
                .isInstanceOfSatisfying(UsageException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(message);
                    assertThat(e.helpHint()).isEqualTo(hint);
                });
    }

    @Test
    void parsesEveryCommandWithDefaults() {
        for (JdocCommand command : JdocCommand.values()) {
            Invocation invocation = command == JdocCommand.INDEX ? run(command, "index")
                    : run(command, command.commandName(), "arg");
            assertThat(invocation.from()).isEmpty();
            assertThat(invocation.page()).isEqualTo(1);
            assertThat(invocation.force()).isFalse();
            assertThat(invocation.argument()).isEqualTo(command == JdocCommand.INDEX ? Optional.empty()
                    : Optional.of("arg"));
        }
    }

    @Test
    void acceptsSeparateAndInlineValuesBeforeAndAfterTheArgument() {
        assertThat(run(JdocCommand.CLASS, "class", "--from", "src/A.java", "X", "--page", "3"))
                .isEqualTo(new Invocation(Optional.of("X"), Optional.of(Path.of("src/A.java")), 3, false));
        assertThat(run(JdocCommand.CLASS, "class", "--page=2", "--from=a b/X.java", "X"))
                .isEqualTo(new Invocation(Optional.of("X"), Optional.of(Path.of("a b/X.java")), 2, false));
        assertThat(run(JdocCommand.METHOD, "method", "Map#of(Object,Object)").argument())
                .contains("Map#of(Object,Object)");
    }

    @Test
    void inlineValueMayContainEqualsSigns() {
        assertThat(run(JdocCommand.FIND, "find", "x", "--from=a=b").from()).contains(Path.of("a=b"));
    }

    @Test
    void valueMayLookLikeANegativeNumberOrStartWithADash() {
        usageError("--page must be at least 1, was -1", "jdoc class --help", "class", "X", "--page", "-1");
        assertThat(run(JdocCommand.FIND, "find", "x", "--from", "-dir/A.java").from())
                .contains(Path.of("-dir/A.java"));
    }

    @Test
    void forceIsAFlag() {
        assertThat(run(JdocCommand.INDEX, "index", "--force").force()).isTrue();
        assertThat(run(JdocCommand.INDEX, "index", "--force=true").force()).isTrue();
        assertThat(run(JdocCommand.INDEX, "index", "--force=FALSE").force()).isFalse();
        assertThat(run(JdocCommand.INDEX, "index", "--from", "x/pom.xml", "--force").from())
                .contains(Path.of("x/pom.xml"));
        usageError("Invalid value for option '--force': 'yes' is not a boolean", "jdoc index --help",
                "index", "--force=yes");
    }

    @Test
    void doubleDashEndsTheOptions() {
        assertThat(run(JdocCommand.FIND, "find", "--", "--page").argument()).contains("--page");
        assertThat(run(JdocCommand.FIND, "find", "--page", "2", "--", "-x").page()).isEqualTo(2);
        assertThat(run(JdocCommand.MEMBER, "member", "-").argument()).contains("-");
        usageError("Unmatched argument at index 3: '--help'", "jdoc find --help", "find", "x", "--", "--help");
    }

    @Test
    void helpAndVersionAtTheTopAndAfterACommand() {
        assertThat(parse("--help")).isEqualTo(new CliRequest.Help(Optional.empty()));
        assertThat(parse("-h")).isEqualTo(new CliRequest.Help(Optional.empty()));
        assertThat(parse("--version")).isEqualTo(new CliRequest.Version());
        assertThat(parse("-V", "ignored")).isEqualTo(new CliRequest.Version());
        assertThat(parse("class", "--help")).isEqualTo(new CliRequest.Help(Optional.of(JdocCommand.CLASS)));
        assertThat(parse("method", "X#y", "-h")).isEqualTo(new CliRequest.Help(Optional.of(JdocCommand.METHOD)));
        assertThat(parse("index", "--version")).isEqualTo(new CliRequest.Version());
    }

    @Test
    void helpWinsOverAMissingArgument() {
        assertThat(parse("source", "--help")).isEqualTo(new CliRequest.Help(Optional.of(JdocCommand.SOURCE)));
    }

    @Test
    void missingCommand() {
        usageError("Missing command, e.g. jdoc class <type>", "jdoc --help");
    }

    @Test
    void unknownCommandAndTopLevelOption() {
        usageError("Unmatched argument at index 0: 'frobnicate'", "jdoc --help", "frobnicate", "x");
        usageError("Unknown option: '--from'", "jdoc --help", "--from", "x", "class", "X");
    }

    @Test
    void unknownOption() {
        usageError("Unknown option: '--bogus'", "jdoc class --help", "class", "--bogus", "X");
        usageError("Unknown option: '-x'", "jdoc class --help", "class", "X", "-x");
        usageError("Unknown option: '--force'", "jdoc class --help", "class", "X", "--force");
        usageError("Unknown option: '--pag'", "jdoc class --help", "class", "X", "--pag=2");
    }

    @Test
    void missingArgument() {
        usageError("Missing required parameter: '<type>'", "jdoc class --help", "class");
        usageError("Missing required parameter: '<type>#<name>[(<params>)]'", "jdoc method --help",
                "method", "--page", "2");
    }

    @Test
    void extraArgument() {
        usageError("Unmatched argument at index 2: 'Y'", "jdoc class --help", "class", "X", "Y");
        usageError("Unmatched argument at index 1: 'x'", "jdoc index --help", "index", "x");
    }

    @Test
    void missingOptionValue() {
        usageError("Missing required parameter for option '--from' (<path>)", "jdoc class --help",
                "class", "X", "--from");
        usageError("Missing required parameter for option '--from' (<path>)", "jdoc class --help",
                "class", "X", "--from", "--page", "2");
        usageError("Missing required parameter for option '--page' (<n>)", "jdoc class --help",
                "class", "X", "--page=");
        usageError("Missing required parameter for option '--page' (<n>)", "jdoc class --help",
                "class", "--page", "--help");
    }

    @Test
    void invalidPage() {
        usageError("Invalid value for option '--page': 'two' is not an int", "jdoc class --help",
                "class", "X", "--page", "two");
        usageError("--page must be at least 1, was 0", "jdoc class --help", "class", "X", "--page", "0");
        usageError("Invalid value for option '--page': '99999999999' is not an int", "jdoc class --help",
                "class", "X", "--page=99999999999");
    }

    @Test
    void invalidPath() {
        usageError("Invalid value for option '--from': 'a\0b' is not a path", "jdoc find --help",
                "find", "x", "--from", "a\0b");
    }

    @Test
    void repeatedOption() {
        usageError("option '--page <n>' should be specified only once", "jdoc class --help",
                "class", "X", "--page", "1", "--page=2");
        usageError("option '--force' should be specified only once", "jdoc index --help",
                "index", "--force", "--force");
    }
}
