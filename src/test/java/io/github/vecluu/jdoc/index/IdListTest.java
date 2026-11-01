package io.github.vecluu.jdoc.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IdListTest {

    @Test
    void rendersJsonArray() {
        assertThat(IdList.json(List.of())).isEqualTo("[]");
        assertThat(IdList.json(List.of(3L, 1L, 20L))).isEqualTo("[3,1,20]");
    }

    @Test
    void escapesLikeWildcards() {
        assertThat(IdList.containsPattern("a_b%c\\d")).isEqualTo("%a\\_b\\%c\\\\d%");
    }

    @Test
    void buildsSubsequenceLikePatterns() {
        assertThat(IdList.subsequencePattern("")).isEqualTo("%");
        assertThat(IdList.subsequencePattern("oET")).isEqualTo("%o%E%T%");
        assertThat(IdList.subsequencePattern("a_%")).isEqualTo("%a%\\_%\\%%");
    }

    @Test
    void rendersJsonStringArrayWithEscapes() {
        assertThat(IdList.jsonStrings(List.of())).isEqualTo("[]");
        assertThat(IdList.jsonStrings(List.of("a", "b$c"))).isEqualTo("[\"a\",\"b$c\"]");
        assertThat(IdList.jsonStrings(List.of("q\"\\\n"))).isEqualTo("[\"q\\\"\\\\\\u000a\"]");
    }
}
