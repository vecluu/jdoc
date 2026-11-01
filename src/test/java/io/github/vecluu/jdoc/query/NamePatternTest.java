package io.github.vecluu.jdoc.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NamePatternTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "HSR|HttpServletRequest",
        "HSRequest|HttpServletRequest",
        "HttpSR|HttpServletRequest",
        "HtSeRe|HttpServletRequest",
        "SR|HttpServletRequest",
        "UD|URLDecoder",
        "ULD|URLDecoder",
        "RLDec|URLDecoder",
        "XHR|XMLHttpRequest",
        "MLHR|XMLHttpRequest",
        "B64E|Base64Encoder",
        "B6E|Base64Encoder",
        "MV|MAX_VALUE",
        "gFB|getFooBar",
        "PT|$ProxyType",
        "M5S|md5sum",
    })
    void matchesCamelHumps(String pattern, String name) {
        assertThat(new NamePattern(pattern).match(name))
                .hasValueSatisfying(match -> assertThat(match.kind()).isEqualTo(MatchKind.CAMEL_HUMPS));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "HSX|HttpServletRequest",
        "RSH|HttpServletRequest",
        "HSRequestX|HttpServletRequest",
        "XR1|XMLHttpRequest",
        "zz|HttpServletRequest",
        "ab|a",
        "hsr|HttpServletRequest",
        "xhr|XMLHttpRequest",
        "be|Base64Encoder",
        "mv|MAX_VALUE",
        "gfb|getFooBar",
    })
    void rejectsNonMatches(String pattern, String name) {
        assertThat(new NamePattern(pattern).match(name)).isEmpty();
        assertThat(new NamePattern(pattern).matches(name)).isFalse();
    }

    @Test
    void classifiesTheMatchKind() {
        assertThat(kind("optional", "Optional")).isEqualTo(MatchKind.EXACT);
        assertThat(kind("Optional", "Optional")).isEqualTo(MatchKind.EXACT);
        assertThat(kind("opt", "OptionalInt")).isEqualTo(MatchKind.PREFIX);
        assertThat(kind("H", "HttpServletRequest")).isEqualTo(MatchKind.PREFIX);
        assertThat(kind("S", "HttpServletRequest")).isEqualTo(MatchKind.CAMEL_HUMPS);
        assertThat(kind("Request", "HttpServletRequest")).isEqualTo(MatchKind.CAMEL_HUMPS);
        assertThat(kind("ervlet", "HttpServletRequest")).isEqualTo(MatchKind.SUBSTRING);
        assertThat(kind("SERVLET", "HttpServletRequest")).isEqualTo(MatchKind.CAMEL_HUMPS);
        assertThat(kind("tpse", "HttpServletRequest")).isEqualTo(MatchKind.SUBSTRING);
        assertThat(kind("6", "Base64Encoder")).isEqualTo(MatchKind.SUBSTRING);
        assertThat(kind("4", "Base64Encoder")).isEqualTo(MatchKind.SUBSTRING);
        assertThat(kind("B6", "Base64Encoder")).isEqualTo(MatchKind.CAMEL_HUMPS);
    }

    @Test
    void ordersExactThenPrefixThenHumpsThenSubstringShorterFirst() {
        NamePattern pattern = new NamePattern("map");
        List<String> names = List.of("TreeMap", "Map", "MapEntry", "HashMap", "Mapper", "MutableAnyPair", "Bitmap",
                "Set");

        Map<String, NameMatch> matches = names.stream()
                .filter(pattern::matches)
                .collect(Collectors.toMap(name -> name, name -> pattern.match(name).orElseThrow()));
        List<String> ordered = matches.keySet().stream()
                .sorted(Comparator.comparing((String name) -> matches.get(name)).thenComparing(name -> name))
                .toList();

        assertThat(ordered).containsExactly("Map", "Mapper", "MapEntry", "Bitmap", "HashMap", "TreeMap");
        assertThat(matches.get("Map")).isEqualTo(new NameMatch(MatchKind.EXACT, 3));
        assertThat(matches.get("HashMap").kind()).isEqualTo(MatchKind.SUBSTRING);
        assertThat(matches.get("Bitmap").kind()).isEqualTo(MatchKind.SUBSTRING);
        assertThat(pattern.usesCamelHumps()).isFalse();
    }

    @Test
    void readsPatternsAsCamelHumpsOnlyWithAnUpperCaseLetter() {
        assertThat(new NamePattern("hsr").matches("HttpServletRequest")).isFalse();
        assertThat(new NamePattern("hSR").usesCamelHumps()).isTrue();
        assertThat(kind("hSR", "HttpServletRequest")).isEqualTo(MatchKind.CAMEL_HUMPS);
        assertThat(kind("MAP", "MutableAnyPair")).isEqualTo(MatchKind.CAMEL_HUMPS);
        assertThat(new NamePattern("map").matches("MutableAnyPair")).isFalse();
        assertThat(kind("Map", "HashMap")).isEqualTo(MatchKind.CAMEL_HUMPS);
    }

    @Test
    void scoresCompareByKindThenLength() {
        assertThat(new NameMatch(MatchKind.PREFIX, 30)).isLessThan(new NameMatch(MatchKind.CAMEL_HUMPS, 3));
        assertThat(new NameMatch(MatchKind.SUBSTRING, 3)).isLessThan(new NameMatch(MatchKind.SUBSTRING, 4));
        assertThat(new NameMatch(MatchKind.EXACT, 5)).isEqualByComparingTo(new NameMatch(MatchKind.EXACT, 5));
    }

    @Test
    void patternIsStrippedAndMustNotBeBlank() {
        assertThat(new NamePattern("  HSR ").pattern()).isEqualTo("HSR");
        assertThat(NamePattern.parse(" ").error()).contains("empty find pattern");
        assertThat(NamePattern.parse(null).isSuccess()).isFalse();
        assertThat(NamePattern.parse("x").orElseThrow().pattern()).isEqualTo("x");
        assertThatThrownBy(() -> new NamePattern("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyOrMissingNamesNeverMatch() {
        assertThat(new NamePattern("a").match("")).isEmpty();
        assertThat(new NamePattern("a").match(null)).isEmpty();
    }

    @Test
    void longNamesAreMatchedQuickly() {
        String name = "A".repeat(2_000) + "b";
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            assertThat(new NamePattern("A".repeat(1_000) + "c").matches(name)).isFalse();
            assertThat(new NamePattern("A".repeat(1_000) + "b").match(name).orElseThrow().kind())
                    .isEqualTo(MatchKind.CAMEL_HUMPS);
        });
    }

    private static MatchKind kind(String pattern, String name) {
        return new NamePattern(pattern).match(name).orElseThrow().kind();
    }
}
