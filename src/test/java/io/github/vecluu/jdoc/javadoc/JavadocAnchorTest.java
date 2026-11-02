package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JavadocAnchorTest {

    private static final TypeName OPTIONAL = TypeName.of("java.util.Optional");

    private static MemberDescriptor method(String name, ParameterInfo... parameters) {
        return MemberDescriptor.builder().declaringType(OPTIONAL).name(name).kind(MemberKind.METHOD)
                .signature("x " + name + "()").parameters(List.of(parameters)).visibility(Visibility.PUBLIC).build();
    }

    private static MemberDescriptor constructor(ParameterInfo... parameters) {
        return MemberDescriptor.builder().declaringType(OPTIONAL).name("Optional").kind(MemberKind.CONSTRUCTOR)
                .signature("Optional()").parameters(List.of(parameters)).visibility(Visibility.PUBLIC).build();
    }

    private static MemberDescriptor field(String name) {
        return MemberDescriptor.builder().declaringType(OPTIONAL).name(name).kind(MemberKind.FIELD)
                .signature("int " + name).visibility(Visibility.PUBLIC).build();
    }

    @Test
    void parsesModernAnchors() {
        assertThat(JavadocAnchor.parse("of(java.lang.Object)")).contains(
                new JavadocAnchor("of", Optional.of(List.of("java.lang.Object"))));
        assertThat(JavadocAnchor.parse("max(T,T)")).contains(new JavadocAnchor("max", Optional.of(List.of("T", "T"))));
        assertThat(JavadocAnchor.parse("empty()")).contains(new JavadocAnchor("empty", Optional.of(List.of())));
        assertThat(JavadocAnchor.parse("<init>(double,com.example.Color)")).contains(
                new JavadocAnchor("<init>", Optional.of(List.of("double", "com.example.Color"))));
        assertThat(JavadocAnchor.parse("%3Cinit%3E(int)")).map(JavadocAnchor::name).contains("<init>");
        assertThat(JavadocAnchor.parse("ORIGIN")).contains(new JavadocAnchor("ORIGIN", Optional.empty()));
    }

    @Test
    void parsesLegacyAnchors() {
        assertThat(JavadocAnchor.parse("of-java.lang.Object-")).contains(
                new JavadocAnchor("of", Optional.of(List.of("java.lang.Object"))));
        assertThat(JavadocAnchor.parse("empty--")).contains(new JavadocAnchor("empty", Optional.of(List.of())));
        assertThat(JavadocAnchor.parse("max-double:A-int-")).contains(
                new JavadocAnchor("max", Optional.of(List.of("double[]", "int"))));
    }

    @Test
    void ignoresNonMemberIds() {
        assertThat(JavadocAnchor.parse("method-summary")).isEmpty();
        assertThat(JavadocAnchor.parse("param-x")).isEmpty();
        assertThat(JavadocAnchor.parse("type-param-T")).isEmpty();
        assertThat(JavadocAnchor.parse("method.detail")).isEmpty();
        assertThat(JavadocAnchor.parse("max(T,T)-type-param-T")).isEmpty();
        assertThat(JavadocAnchor.parse("")).isEmpty();
        assertThat(JavadocAnchor.parse(null)).isEmpty();
    }

    @Test
    void matchesByErasedOrDeclaredSimpleTypes() {
        MemberDescriptor max = method("max", new ParameterInfo("a", "T", "Comparable"),
                new ParameterInfo("b", "T", "Comparable"));
        MemberDescriptor entries = method("put", new ParameterInfo("e", "Map.Entry<K, V>", "Map.Entry"),
                new ParameterInfo("values", "String...", "String[]"));

        assertThat(JavadocAnchor.parse("max(T,T)").orElseThrow().matches(max)).isTrue();
        assertThat(JavadocAnchor.parse("max(java.lang.Comparable,java.lang.Comparable)").orElseThrow().matches(max))
                .isTrue();
        assertThat(JavadocAnchor.parse("max(int,int)").orElseThrow().matches(max)).isFalse();
        assertThat(JavadocAnchor.parse("max(T)").orElseThrow().matches(max)).isFalse();
        assertThat(JavadocAnchor.parse("put(java.util.Map.Entry,java.lang.String...)").orElseThrow()
                .matches(entries)).isTrue();
        assertThat(JavadocAnchor.parse("put-java.util.Map.Entry-java.lang.String:A-").orElseThrow()
                .matches(entries)).isTrue();
    }

    @Test
    void matchesConstructorsAndFields() {
        MemberDescriptor ctor = constructor(new ParameterInfo("v", "T", "Object"));

        assertThat(JavadocAnchor.parse("<init>(java.lang.Object)").orElseThrow().matches(ctor)).isTrue();
        assertThat(JavadocAnchor.parse("Optional-java.lang.Object-").orElseThrow().matches(ctor)).isTrue();
        assertThat(JavadocAnchor.parse("EMPTY").orElseThrow().matches(field("EMPTY"))).isTrue();
        assertThat(JavadocAnchor.parse("EMPTY()").orElseThrow().matches(field("EMPTY"))).isFalse();
        assertThat(JavadocAnchor.parse("EMPTY").orElseThrow().matches(method("EMPTY"))).isFalse();
    }

    @Test
    void looseMatchingUsesNameAndArity() {
        MemberDescriptor max = method("max", new ParameterInfo("a", "int", "int"));

        assertThat(JavadocAnchor.parse("max(SomethingElse)").orElseThrow().matchesLoosely(max)).isTrue();
        assertThat(JavadocAnchor.parse("max(a,b)").orElseThrow().matchesLoosely(max)).isFalse();
        assertThat(JavadocAnchor.parse("min(int)").orElseThrow().matchesLoosely(max)).isFalse();
    }

    @Test
    void simpleFormDropsPackagesGenericsAndVarargs() {
        assertThat(JavadocAnchor.simpleForm("java.util.Map.Entry<K, V>")).isEqualTo("Entry");
        assertThat(JavadocAnchor.simpleForm("java.lang.String...")).isEqualTo("String[]");
        assertThat(JavadocAnchor.simpleForm("int:A")).isEqualTo("int[]");
        assertThat(JavadocAnchor.simpleForm("List<String>[]")).isEqualTo("List[]");
    }
}
