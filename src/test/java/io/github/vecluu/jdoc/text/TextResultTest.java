package io.github.vecluu.jdoc.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextResultTest {

    @Test
    void builderKeepsLinesAndBlocksInOrder() {
        TextResult result = TextResult.builder("header")
                .line("one")
                .blankLine()
                .block(List.of("b1", "b2"))
                .lines(List.of("x", "y"))
                .block(TextBlock.of("z"))
                .build();

        assertThat(result.blocks()).hasSize(6);
        assertThat(result.blocks().get(2).lines()).containsExactly("b1", "b2");
        assertThat(result.bodyLines()).containsExactly("one", "", "b1", "b2", "x", "y", "z");
        assertThat(result.allLines()).first().isEqualTo("header");
        assertThat(result.text()).isEqualTo("header\none\n\nb1\nb2\nx\ny\nz");
    }

    @Test
    void ofLinesMakesOneBlockPerLine() {
        TextResult result = TextResult.ofLines("h", List.of("a", "b"));

        assertThat(result.blocks()).containsExactly(TextBlock.line("a"), TextBlock.line("b"));
    }

    @Test
    void isImmutable() {
        List<String> lines = new ArrayList<>(List.of("a"));
        TextBlock block = new TextBlock(lines);
        lines.add("b");

        assertThat(block.lines()).containsExactly("a");
        assertThatThrownBy(() -> block.lines().add("c")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new TextResult("h", null).blocks()).isEmpty();
        assertThat(new TextBlock(null).isEmpty()).isTrue();
    }

    @Test
    void rejectsLineBreaksInsideLines() {
        assertThatThrownBy(() -> TextBlock.of("a\nb")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TextResult("h\r", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextBlock.line(null)).isInstanceOf(NullPointerException.class);
    }
}
