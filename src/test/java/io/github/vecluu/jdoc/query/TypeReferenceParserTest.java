package io.github.vecluu.jdoc.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TypeReferenceParserTest {

    private final TypeReferenceParser parser = new TypeReferenceParser();

    @Test
    void simpleNameIsARelativeLookup() {
        TypeReference reference = parse("Optional");

        assertThat(reference.isSimpleName()).isTrue();
        assertThat(reference.simpleName()).isEqualTo("Optional");
        assertThat(reference.candidates()).containsExactly(relative("Optional"));
    }

    @Test
    void fqcnTriesTheConventionalSplitFirst() {
        TypeReference reference = parse("java.util.Optional");

        assertThat(reference.isSimpleName()).isFalse();
        assertThat(reference.names()).containsExactly("java", "util", "Optional");
        assertThat(reference.candidates()).containsExactly(
                exact("java.util.Optional"), exact("java.util$Optional"), exact("java$util$Optional"));
    }

    @Test
    void dottedNestedTypeOffersNestedAndTopLevelReadings() {
        TypeReference reference = parse("java.util.Map.Entry");

        assertThat(reference.candidates()).containsExactly(
                exact("java.util.Map$Entry"),
                exact("java.util.Map.Entry"),
                exact("java.util$Map$Entry"),
                exact("java$util$Map$Entry"));
        assertThat(reference.simpleName()).isEqualTo("Entry");
    }

    @Test
    void outerDotInnerIsARelativeLookupFirst() {
        TypeReference reference = parse("Map.Entry");

        assertThat(reference.candidates()).containsExactly(relative("Map", "Entry"), exact("Map.Entry"));
    }

    @Test
    void dollarAlwaysMeansNesting() {
        assertThat(parse("Map$Entry").candidates()).containsExactly(relative("Map", "Entry"));
        assertThat(parse("java.util.Map$Entry").candidates()).containsExactly(
                exact("java.util.Map$Entry"), exact("java.util$Map$Entry"), exact("java$util$Map$Entry"));
        assertThat(parse("a.Outer$Inner$Deep").candidates()).containsExactly(exact("a.Outer$Inner$Deep"),
                exact("a$Outer$Inner$Deep"));
    }

    @Test
    void dollarAtTheEdgesIsPartOfTheName() {
        assertThat(parse("$Proxy").candidates()).containsExactly(relative("$Proxy"));
        assertThat(parse("a.b.Foo$").candidates().getFirst()).isEqualTo(exact("a.b.Foo$"));
    }

    @Test
    void lowerCaseSegmentsOnlyAreTriedAsTopLevelTypeFirst() {
        assertThat(parse("a.b.c").candidates()).containsExactly(exact("a.b.c"), exact("a.b$c"), exact("a$b$c"));
    }

    @Test
    void typeArgumentsAndSurroundingWhitespaceAreIgnored() {
        TypeReference reference = parse("  java.util.Map<String, List<Integer>>  ");

        assertThat(reference.input()).isEqualTo("java.util.Map<String, List<Integer>>");
        assertThat(reference.candidates().getFirst()).isEqualTo(exact("java.util.Map"));
        assertThat(parse("Optional<T>").candidates()).containsExactly(relative("Optional"));
    }

    @Test
    void relativeCandidatesMatchBySimpleNameSuffix() {
        TypeReference entry = parse("Entry");
        TypeReference mapEntry = parse("Map.Entry");

        assertThat(entry.matches(TypeName.of("java.util.Map$Entry"))).isTrue();
        assertThat(entry.matches(TypeName.of("a.Entry"))).isTrue();
        assertThat(entry.matches(TypeName.of("a.Entryway"))).isFalse();
        assertThat(mapEntry.matches(TypeName.of("java.util.Map$Entry"))).isTrue();
        assertThat(mapEntry.matches(TypeName.of("x.Outer$Map$Entry"))).isTrue();
        assertThat(mapEntry.matches(TypeName.of("a.Entry"))).isFalse();
        assertThat(mapEntry.matches(TypeName.of("b.HashMap$Entry"))).isFalse();
        assertThat(mapEntry.matches(TypeName.of("Map.Entry"))).isTrue();
    }

    @Test
    void exactCandidatesMatchOnlyTheirName() {
        TypeReference reference = parse("java.util.Optional");

        assertThat(reference.matches(TypeName.of("java.util.Optional"))).isTrue();
        assertThat(reference.matches(TypeName.of("com.google.common.base.Optional"))).isFalse();
        assertThat(reference.candidates().getFirst().simpleName()).isEqualTo("Optional");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "java..util", ".Foo", "Foo.", "1Foo", "Foo Bar", "Optional<T", "Foo>",
        "Foo-Bar", "Foo[]", "a.b.#c", "<T>"})
    void rejectsMalformedNames(String input) {
        ParseResult<TypeReference> result = parser.parse(input);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).hasValueSatisfying(message -> assertThat(message).isNotBlank());
    }

    @ParameterizedTest
    @NullSource
    void rejectsNull(String input) {
        assertThat(parser.parse(input).error()).contains("empty type name");
    }

    @Test
    void errorMessagesNameTheProblem() {
        assertThat(parser.parse("java..util").error()).hasValueSatisfying(m -> assertThat(m).contains("empty"));
        assertThat(parser.parse("1Foo").error()).hasValueSatisfying(m -> assertThat(m).contains("'1Foo'"));
        assertThat(parser.parse("List<T").error()).hasValueSatisfying(m -> assertThat(m).contains("unbalanced"));
    }

    private TypeReference parse(String input) {
        return parser.parse(input).orElseThrow();
    }

    private static TypeCandidate exact(String binaryName) {
        return new TypeCandidate.Exact(TypeName.of(binaryName));
    }

    private static TypeCandidate relative(String... names) {
        return new TypeCandidate.Relative(List.of(names));
    }
}
