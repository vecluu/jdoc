package io.github.vecluu.jdoc.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandLineTextTest {

    @Test
    void plainArgumentsStayUnquoted() {
        assertThat(CommandLineText.of(List.of("method", "java.util.Optional#orElseThrow", "--from", "src/A.java")))
                .isEqualTo("jdoc method java.util.Optional#orElseThrow --from src/A.java");
    }

    @Test
    void shellSpecialArgumentsAreSingleQuoted() {
        assertThat(CommandLineText.quote("Optional#of(int, int)")).isEqualTo("'Optional#of(int, int)'");
        assertThat(CommandLineText.quote("Map$Entry")).isEqualTo("'Map$Entry'");
        assertThat(CommandLineText.quote("Optional<T>")).isEqualTo("'Optional<T>'");
        assertThat(CommandLineText.quote("#comment")).isEqualTo("'#comment'");
        assertThat(CommandLineText.quote("it's")).isEqualTo("'it'\\''s'");
        assertThat(CommandLineText.quote("")).isEqualTo("''");
    }

    @Test
    void noArguments() {
        assertThat(CommandLineText.of(List.of())).isEqualTo("jdoc");
    }
}
