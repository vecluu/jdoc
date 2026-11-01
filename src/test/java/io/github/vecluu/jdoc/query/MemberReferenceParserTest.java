package io.github.vecluu.jdoc.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberReferenceParserTest {

    private final MemberReferenceParser parser = new MemberReferenceParser();

    @Test
    void noParenthesesMeansAllOverloads() {
        MemberReference reference = parse("Optional#of");

        assertThat(reference.name()).isEqualTo("of");
        assertThat(reference.constructor()).isFalse();
        assertThat(reference.allOverloads()).isTrue();
        assertThat(reference.parameterTypes()).isEmpty();
        assertThat(reference.type().simpleName()).isEqualTo("Optional");
        assertThat(reference.input()).isEqualTo("Optional#of");
    }

    @Test
    void emptyParenthesesMeanZeroParameters() {
        MemberReference reference = parse("Optional#empty()");

        assertThat(reference.allOverloads()).isFalse();
        assertThat(reference.parameterTypes()).contains(List.of());
        assertThat(parse("Optional#empty(   )").parameterTypes()).contains(List.of());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "java.util.Optional#of(Object)|Object",
        "Math#max(int,int)|int,int",
        "Math#max( int , int )|int,int",
        "Collections#sort(List<T>, Comparator<? super T>)|List,Comparator",
        "Map#ofEntries(Map.Entry<? extends K, ? extends V>...)|Entry[]",
        "String#format(String, Object...)|String,Object[]",
        "Foo#bar(java.util.List<java.lang.String>)|List",
        "Foo#bar(int[][], String [ ])|int[][],String[]",
        "Foo#bar(int count, String name)|int,String",
        "Foo#bar(Map<String, Map<K, V>>, int)|Map,int",
        "Foo#bar(Map$Entry)|Entry",
    })
    void normalizesParameters(String input, String expected) {
        assertThat(parse(input).parameterTypes()).contains(List.of(expected.split(",")));
    }

    @Test
    void toleratesWhitespaceEverywhere() {
        MemberReference reference = parse("  java.util.Optional # of ( Object )  ");

        assertThat(reference.name()).isEqualTo("of");
        assertThat(reference.parameterTypes()).contains(List.of("Object"));
        assertThat(reference.type().candidates().getFirst().simpleName()).isEqualTo("Optional");
    }

    @Test
    void constructorBySimpleTypeName() {
        MemberReference reference = parse("java.util.ArrayList#ArrayList(int)");

        assertThat(reference.constructor()).isTrue();
        assertThat(reference.name()).isEqualTo("ArrayList");
        assertThat(reference.parameterTypes()).contains(List.of("int"));
    }

    @Test
    void constructorByInitName() {
        MemberReference reference = parse("ArrayList#<init>");

        assertThat(reference.constructor()).isTrue();
        assertThat(reference.name()).isEqualTo("ArrayList");
        assertThat(reference.allOverloads()).isTrue();
        assertThat(parse("java.util.Map.Entry#<init>()").name()).isEqualTo("Entry");
        assertThat(parse("Map$Entry#Entry").constructor()).isTrue();
        assertThat(parse("ArrayList<E>#ArrayList").constructor()).isTrue();
    }

    @Test
    void methodWithOtherNameIsNoConstructor() {
        assertThat(parse("ArrayList#arrayList").constructor()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Foo", "#bar", "Foo#", "Foo# ", "Foo#bar(", "Foo#bar(int", "Foo#bar)", "Foo#bar(int))",
        "Foo#bar(int,)", "Foo#bar(,int)", "Foo#1bar", "Foo#bar(int) x", "Foo#ba r", "Foo#bar#baz",
        "Foo#bar(List<String)", "Foo#bar(int a b)", "..Foo#bar", "Foo#bar(a.)", "Foo#<clinit>", "Foo#bar((int))",
        "Foo#(int)"})
    void rejectsMalformedReferences(String input) {
        ParseResult<MemberReference> result = parser.parse(input);

        assertThat(result.isSuccess()).as(input).isFalse();
        assertThat(result.error()).hasValueSatisfying(message -> assertThat(message).isNotBlank());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingInput(String input) {
        assertThat(parser.parse(input).error()).hasValueSatisfying(m -> assertThat(m).contains("<type>#<name>"));
    }

    @Test
    void errorMessagesAreDescriptive() {
        assertThat(parser.parse("Optional.of").error())
                .contains("'Optional.of' has no '#', expected <type>#<name>[(<params>)]");
        assertThat(parser.parse("Foo#bar(int").error())
                .contains("parameter list is not closed with ')' in 'Foo#bar(int'");
        assertThat(parser.parse("Foo#bar(int,)").error()).contains("empty parameter type");
        assertThat(parser.parse("1Foo#bar").error()).hasValueSatisfying(m -> assertThat(m).contains("'1Foo'"));
    }

    @Test
    void referenceValueIsImmutable() {
        MemberReference reference = parse("Foo#bar(int)");

        MemberReference copy = new MemberReference(reference.input(), reference.type(), "bar", false, null);

        assertThat(copy.parameterTypes()).isEqualTo(Optional.empty());
        assertThat(reference.parameterTypes().orElseThrow()).isUnmodifiable();
    }

    private MemberReference parse(String input) {
        return parser.parse(input).orElseThrow();
    }
}
