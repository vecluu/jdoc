package io.github.vecluu.jdoc.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.text.ColumnLayout.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnLayoutTest {

    @Test
    void padsTheLeftColumnToTheWidestEntry() {
        List<String> lines = new ColumnLayout().format(List.of(
                Row.of("java.time.Duration", "jdk 25"),
                Row.of("javax.xml.datatype.Duration", "jdk 25")), "  ");

        assertThat(lines).containsExactly(
                "  java.time.Duration           jdk 25",
                "  javax.xml.datatype.Duration  jdk 25");
    }

    @Test
    void entriesWiderThanTheCapGetJustTheGap() {
        ColumnLayout layout = new ColumnLayout(20, 2);

        List<String> lines = layout.format(List.of(
                Row.of("T get()", "Returns the value."),
                Row.of("static <T> Optional<T> ofNullable(T value)", "Returns an Optional."),
                Row.of("boolean isPresent()", "If a value is present.")));

        assertThat(lines).containsExactly(
                "T get()              Returns the value.",
                "static <T> Optional<T> ofNullable(T value)  Returns an Optional.",
                "boolean isPresent()  If a value is present.");
    }

    @Test
    void wideEntriesDoNotWidenTheColumn() {
        List<String> lines = new ColumnLayout(5, 3).format(List.of(
                Row.of("abcdefgh", "x"),
                Row.of("abc", "y")));

        assertThat(lines).containsExactly("abcdefgh   x", "abc   y");
    }

    @Test
    void entryExactlyAtTheCapIsAligned() {
        List<String> lines = new ColumnLayout(5, 2).format(List.of(Row.of("abcde", "x"), Row.of("a", "y")));

        assertThat(lines).containsExactly("abcde  x", "a      y");
    }

    @Test
    void emptyRightColumnHasNoTrailingSpaces() {
        List<String> lines = new ColumnLayout().format(List.of(Row.of("longer left", "x"), Row.of("short", null)));

        assertThat(lines).containsExactly("longer left  x", "short");
    }

    @Test
    void countsCodePointsNotCharUnits() {
        List<String> lines = new ColumnLayout().format(List.of(Row.of("a𝕏", "x"), Row.of("abc", "y")));

        assertThat(lines).containsExactly("a𝕏   x", "abc  y");
    }

    @Test
    void noRowsGiveNoLines() {
        assertThat(new ColumnLayout().format(List.of())).isEmpty();
    }

    @Test
    void rejectsInvalidSettingsAndMultiLineCells() {
        assertThatThrownBy(() -> new ColumnLayout(-1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ColumnLayout(10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Row.of("a\nb", "c")).isInstanceOf(IllegalArgumentException.class);
    }
}
