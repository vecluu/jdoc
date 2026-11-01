package io.github.vecluu.jdoc.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ParameterTypesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "int|int",
        "String|String",
        "  String  |String",
        "List<String>|List",
        "Map<String, List<Integer>>|Map",
        "java.util.List<java.lang.String>|List",
        "java.lang.String|String",
        "Map.Entry|Entry",
        "java.util.Map.Entry<K, V>|Entry",
        "Map$Entry|Entry",
        "String...|String[]",
        "Map.Entry<? extends K, ? extends V>...|Entry[]",
        "int[]|int[]",
        "int[][]|int[][]",
        "String [ ]|String[]",
        "String ...|String[]",
        "java . util . List|List",
        "int count|int",
        "List<String> names|List",
        "String... args|String[]",
        "T|T",
        "$Proxy|$Proxy",
    })
    void normalizesUserInput(String input, String expected) {
        assertThat(ParameterTypes.normalize(input).orElseThrow()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "List<String", "List>", "1int", "int a b", "a.", ".a", "int[", "int]",
        "String....", "@Nullable String", "int-1"})
    void rejectsMalformedParameters(String input) {
        assertThat(ParameterTypes.normalize(input).error()).isPresent();
    }

    @Test
    void rejectsNull() {
        assertThat(ParameterTypes.normalize(null).error()).contains("empty parameter type");
    }

    @Test
    void lenientNormalizationFallsBackToTheText() {
        assertThat(ParameterTypes.normalizeLenient(" java.util.List<String> ")).isEqualTo("List");
        assertThat(ParameterTypes.normalizeLenient(" @Nullable String ")).isEqualTo("@Nullable String");
        assertThat(ParameterTypes.normalizeLenient(null)).isEmpty();
    }
}
