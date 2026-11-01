package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BlockTagFormatterTest {

    private final BlockTagFormatter formatter = new BlockTagFormatter();

    @Test
    void standardTags() {
        assertThat(formatter.param("value", "the value,\n   may be `null`")).isEqualTo("@param value the value, may be `null`");
        assertThat(formatter.param("<T>", "the type")).isEqualTo("@param <T> the type");
        assertThat(formatter.returns("the result")).isEqualTo("@return the result");
        assertThat(formatter.throwsTag("IllegalArgumentException", "if bad")).isEqualTo(
                "@throws IllegalArgumentException if bad");
        assertThat(formatter.since("1.8")).isEqualTo("@since 1.8");
        assertThat(formatter.deprecated("use `bar`\n\ninstead")).isEqualTo("@deprecated use `bar` instead");
        assertThat(formatter.see("Foo#bar(int)")).isEqualTo("@see Foo#bar(int)");
    }

    @Test
    void emptyTextYieldsBareTag() {
        assertThat(formatter.returns("")).isEqualTo("@return");
        assertThat(formatter.deprecated(null)).isEqualTo("@deprecated");
        assertThat(formatter.param("x", " ")).isEqualTo("@param x");
    }

    @Test
    void genericTagsAcceptOptionalAt() {
        assertThat(formatter.line("apiNote", "a note")).isEqualTo("@apiNote a note");
        assertThat(formatter.line("@implSpec", "spec")).isEqualTo("@implSpec spec");
        assertThatThrownBy(() -> formatter.line(" ", "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
