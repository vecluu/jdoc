package io.github.vecluu.jdoc.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParseResultTest {

    @Test
    void successCarriesTheValue() {
        ParseResult<String> result = ParseResult.success("x");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).contains("x");
        assertThat(result.error()).isEmpty();
        assertThat(result.orElseThrow()).isEqualTo("x");
        assertThat(result.map(String::length).value()).contains(1);
    }

    @Test
    void failureCarriesTheMessage() {
        ParseResult<String> result = ParseResult.failure("bad input");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.value()).isEmpty();
        assertThat(result.error()).contains("bad input");
        assertThat(result.map(String::length).error()).contains("bad input");
        assertThatThrownBy(result::orElseThrow).isInstanceOf(IllegalStateException.class).hasMessage("bad input");
    }

    @Test
    void rejectsNulls() {
        assertThatThrownBy(() -> ParseResult.success(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ParseResult.failure(null)).isInstanceOf(NullPointerException.class);
    }
}
