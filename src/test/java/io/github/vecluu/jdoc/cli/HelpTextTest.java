package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HelpTextTest {

    @Test
    void overviewListsEveryCommandWithItsArgumentTheOptionsAndTheExitCodes() {
        String help = HelpText.overview();

        assertThat(help.lines().findFirst()).contains("Usage: jdoc <command> [<argument>] [--from <path>] "
                + "[--page <n>]");
        assertThat(help).contains(
                "  find <pattern>  ", "  class <type>  ", "  method <type>#<name>[(<params>)]  ",
                "  source <type>#<name>[(<params>)]  ", "  impls <type>  ", "  member <name>  ",
                "  package <pkg>  ", "  index [--force]  ",
                "  --from <path>  ", "  --page <n>  ", "  -h, --help  ", "  -V, --version  ",
                "Exit codes: 0 found, 1 not found, 2 ambiguous, 3 error.");
        assertThat(help).doesNotEndWith("\n");
    }

    @Test
    void commandDescriptionsStartInOneColumn() {
        String help = HelpText.overview();

        assertThat(Arrays.stream(JdocCommand.values())
                .map(command -> help.lines().filter(line -> line.endsWith(command.description())).findFirst()
                        .orElseThrow().length() - command.description().length())
                .distinct()).hasSize(1);
    }

    @Test
    void commandHelpShowsUsageArgumentAndOptions() {
        String help = HelpText.of(JdocCommand.CLASS);

        assertThat(help.lines().toList()).startsWith(
                "Usage: jdoc class <type> [--from <path>] [--page <n>]",
                JdocCommand.CLASS.description());
        assertThat(help).contains("  <type>  ", "  --from <path>  ", "  --page <n>  ", "  -h, --help  ",
                "Exit codes: 0 found, 1 not found, 2 ambiguous, 3 error.");
    }

    @Test
    void indexHelpShowsForceAndNoArgument() {
        String help = HelpText.of(JdocCommand.INDEX);

        assertThat(help.lines().findFirst()).contains("Usage: jdoc index [--force] [--from <path>] [--page <n>]");
        assertThat(help).contains("  --force  ").doesNotContain("<argument>");
    }
}
