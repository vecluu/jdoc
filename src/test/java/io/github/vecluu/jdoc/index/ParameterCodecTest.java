package io.github.vecluu.jdoc.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.IndexException;
import io.github.vecluu.jdoc.model.ParameterInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParameterCodecTest {

    @Test
    void emptyListIsEmptyString() {
        assertThat(ParameterCodec.encode(List.of())).isEmpty();
        assertThat(ParameterCodec.decode("")).isEmpty();
        assertThat(ParameterCodec.decode(null)).isEmpty();
    }

    @Test
    void roundTripsTypesWithCommasSpacesAndGenerics() {
        List<ParameterInfo> parameters = List.of(
                new ParameterInfo("map", "Map<String, List<? extends Number>>", "Map"),
                new ParameterInfo("values", "String...", "String[]"),
                new ParameterInfo("entry", "Map.Entry<K,V>", "Map.Entry"));
        assertThat(ParameterCodec.decode(ParameterCodec.encode(parameters))).isEqualTo(parameters);
    }

    @Test
    void roundTripsSingleParameter() {
        List<ParameterInfo> parameters = List.of(new ParameterInfo("x", "int", "int"));
        assertThat(ParameterCodec.decode(ParameterCodec.encode(parameters))).isEqualTo(parameters);
    }

    @Test
    void rejectsSeparatorCharacters() {
        assertThatThrownBy(() -> ParameterCodec.encode(List.of(new ParameterInfo("a\u001Fb", "int", "int"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCorruptText() {
        assertThatThrownBy(() -> ParameterCodec.decode("a\u001Fint")).isInstanceOf(IndexException.class);
    }
}
