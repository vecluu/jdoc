package io.github.vecluu.jdoc.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.text.TextResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandResultTest {

    @Test
    void factoriesSetExitCodes() {
        TextResult text = TextResult.ofLines("h", List.of());
        assertThat(CommandResult.found(text).exitCode().code()).isZero();
        assertThat(CommandResult.notFound(text).exitCode().code()).isEqualTo(1);
        assertThat(CommandResult.ambiguous(text).exitCode().code()).isEqualTo(2);
        assertThat(CommandResult.error("boom").exitCode().code()).isEqualTo(3);
    }

    @Test
    void errorHasOneStderrLineAndNoOutput() {
        CommandResult error = CommandResult.error("no pom.xml");

        assertThat(error.output()).isEmpty();
        assertThat(error.errorOutput()).containsExactly("error: no pom.xml");
    }

    @Test
    void warningsComeBeforeOwnStderrLines() {
        CommandResult result = CommandResult.error("x").withWarnings(List.of("warning: w"));

        assertThat(result.errorOutput()).containsExactly("warning: w", "error: x");
        assertThat(result.withWarnings(List.of())).isSameAs(result);
    }
}
