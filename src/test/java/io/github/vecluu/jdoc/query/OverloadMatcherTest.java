package io.github.vecluu.jdoc.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OverloadMatcherTest {

    private final OverloadMatcher matcher = new OverloadMatcher();
    private final MemberReferenceParser parser = new MemberReferenceParser();

    @Test
    void absentParameterListMatchesEveryOverload() {
        assertThat(matcher.matches(Optional.empty(), List.of())).isTrue();
        assertThat(matcher.matches(Optional.empty(), List.of(param("int", "int")))).isTrue();
    }

    @Test
    void arityMustMatch() {
        assertThat(matcher.matches(List.of(), List.of())).isTrue();
        assertThat(matcher.matches(List.of(), List.of(param("int", "int")))).isFalse();
        assertThat(matcher.matches(List.of("int"), List.of())).isFalse();
        assertThat(matcher.matches(List.of("int", "int"), List.of(param("int", "int")))).isFalse();
    }

    @Test
    void comparesErasedSimpleNamesCaseSensitively() {
        List<ParameterInfo> params = List.of(param("List<String>", "List"), param("int", "int"));

        assertThat(matcher.matches(List.of("List", "int"), params)).isTrue();
        assertThat(matcher.matches(List.of("list", "int"), params)).isFalse();
        assertThat(matcher.matches(List.of("Collection", "int"), params)).isFalse();
        assertThat(matcher.matches(List.of("int", "List"), params)).isFalse();
    }

    @Test
    void varargsAndArraysAreEquivalent() {
        List<ParameterInfo> varargs = List.of(param("String", "String"), param("Object...", "Object[]"));

        assertThat(matcher.matches(query("F#f(String, Object...)"), varargs)).isTrue();
        assertThat(matcher.matches(query("F#f(String, Object[])"), varargs)).isTrue();
        assertThat(matcher.matches(query("F#f(String, Object)"), varargs)).isFalse();
        assertThat(matcher.matches(query("F#f(int[])"), List.of(param("int[]", "int[]")))).isTrue();
        assertThat(matcher.matches(query("F#f(int)"), List.of(param("int[]", "int[]")))).isFalse();
        assertThat(matcher.matches(query("F#f(int[])"), List.of(param("int[][]", "int[][]")))).isFalse();
    }

    @Test
    void genericInputIsErased() {
        List<ParameterInfo> params = List.of(param("Map<String, List<Integer>>", "Map"));

        assertThat(matcher.matches(query("F#f(Map<K, V>)"), params)).isTrue();
        assertThat(matcher.matches(query("F#f(java.util.Map)"), params)).isTrue();
    }

    @Test
    void typeVariablesMatchByNameOrErasure() {
        List<ParameterInfo> params = List.of(param("T", "Object"));

        assertThat(matcher.matches(query("F#f(T)"), params)).isTrue();
        assertThat(matcher.matches(query("F#f(Object)"), params)).isTrue();
        assertThat(matcher.matches(query("F#f(String)"), params)).isFalse();
        assertThat(matcher.matches(query("F#f(Comparable)"), List.of(param("T", "Comparable")))).isTrue();
    }

    @Test
    void nestedTypesMatchWithOrWithoutOuterName() {
        List<ParameterInfo> qualifiedErasure = List.of(param("Map.Entry<K, V>", "Map.Entry"));
        List<ParameterInfo> simpleErasure = List.of(param("Entry<K, V>", "Entry"));

        for (List<ParameterInfo> params : List.of(qualifiedErasure, simpleErasure)) {
            assertThat(matcher.matches(query("F#f(Entry)"), params)).isTrue();
            assertThat(matcher.matches(query("F#f(Map.Entry)"), params)).isTrue();
            assertThat(matcher.matches(query("F#f(java.util.Map.Entry<K, V>)"), params)).isTrue();
            assertThat(matcher.matches(query("F#f(Map)"), params)).isFalse();
        }
        assertThat(matcher.matches(query("F#f(Entry[])"), List.of(param("Map.Entry<K,V>...", "Map.Entry[]"))))
                .isTrue();
    }

    @Test
    void qualifiedAndUnparseableMemberTypesAreHandled() {
        assertThat(matcher.matches(List.of("String"), List.of(param("java.lang.String", "java.lang.String"))))
                .isTrue();
        assertThat(matcher.matches(List.of("String"), List.of(param("@Nullable String", "String")))).isTrue();
    }

    @Test
    void matchesMembersByNameKindAndParameters() {
        MemberDescriptor of = method("of", param("T", "Object"));
        MemberDescriptor empty = method("empty");
        MemberDescriptor constructor = MemberDescriptor.builder()
                .declaringType(OPTIONAL).name("Optional").kind(MemberKind.CONSTRUCTOR)
                .signature("Optional(T value)").parameters(List.of(param("T", "Object")))
                .visibility(Visibility.PRIVATE).build();
        MemberDescriptor field = MemberDescriptor.builder()
                .declaringType(OPTIONAL).name("EMPTY").kind(MemberKind.FIELD)
                .signature("static final Optional<?> EMPTY").visibility(Visibility.PRIVATE).build();

        assertThat(matcher.matches(reference("Optional#of"), of)).isTrue();
        assertThat(matcher.matches(reference("Optional#of(Object)"), of)).isTrue();
        assertThat(matcher.matches(reference("Optional#of()"), of)).isFalse();
        assertThat(matcher.matches(reference("Optional#of"), empty)).isFalse();
        assertThat(matcher.matches(reference("Optional#empty()"), empty)).isTrue();
        assertThat(matcher.matches(reference("Optional#<init>(T)"), constructor)).isTrue();
        assertThat(matcher.matches(reference("Optional#<init>"), constructor)).isTrue();
        assertThat(matcher.matches(reference("Optional#<init>()"), constructor)).isFalse();
        assertThat(matcher.matches(reference("Optional#EMPTY"), field)).isTrue();
        assertThat(matcher.matches(reference("Optional#EMPTY()"), field)).isFalse();
    }

    private static final TypeName OPTIONAL = TypeName.of("java.util.Optional");

    private static MemberDescriptor method(String name, ParameterInfo... params) {
        return MemberDescriptor.builder()
                .declaringType(OPTIONAL).name(name).kind(MemberKind.METHOD)
                .signature(name + "()").parameters(List.of(params))
                .visibility(Visibility.PUBLIC).build();
    }

    private MemberReference reference(String input) {
        return parser.parse(input).orElseThrow();
    }

    private List<String> query(String input) {
        return reference(input).parameterTypes().orElseThrow();
    }

    private static ParameterInfo param(String type, String erased) {
        return new ParameterInfo("p", type, erased);
    }
}
