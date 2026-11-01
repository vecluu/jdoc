package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlatJsonTest {

    @Test
    void roundTripsStringsAndNumbers() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("text", "a \"quoted\" \\ path\nwith\ttabs and \u0001 control and ünïcode");
        fields.put("count", 42L);
        fields.put("negative", -7L);

        String json = FlatJson.write(fields);

        assertThat(FlatJson.read(json)).containsExactlyEntriesOf(fields);
    }

    @Test
    void readsCompactJsonWithEscapesAndLiterals() {
        Map<String, Object> fields = FlatJson.read(
                "{\"a\":\"x\\/y\\u0041\",\"b\":1.5,\"c\":true,\"d\":null,\"e\":-3}");

        assertThat(fields).containsEntry("a", "x/yA").containsEntry("b", 1.5).containsEntry("e", -3L)
                .doesNotContainKeys("c", "d");
    }

    @Test
    void readsEmptyObject() {
        assertThat(FlatJson.read("  { }  ")).isEmpty();
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> FlatJson.read("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlatJson.read("{\"a\":")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlatJson.read("{\"a\":[1]}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlatJson.read("{\"a\":{}}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlatJson.read("{\"a\":1} x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlatJson.read("{\"a\":\"\\q\"}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FlatJson.read("{\"a\" 1}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedValuesWhenWriting() {
        assertThatThrownBy(() -> FlatJson.write(Map.of("a", new Object())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
