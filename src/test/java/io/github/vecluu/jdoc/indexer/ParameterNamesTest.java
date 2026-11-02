package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParameterNamesTest {

    private static final TypeName TYPE = TypeName.of("p.Util");

    private static MemberDescriptor member(MemberKind kind, String name, String signature,
                                           ParameterInfo... parameters) {
        return MemberDescriptor.builder().declaringType(TYPE).name(name).kind(kind).signature(signature)
                .parameters(List.of(parameters)).visibility(Visibility.PUBLIC).build();
    }

    private static final MemberDescriptor JOIN = member(MemberKind.METHOD, "join",
            "static <T> String join(Map.Entry<T, String>[] arg0, String... arg1) throws IOException",
            new ParameterInfo("arg0", "Map.Entry<T, String>[]", "Map.Entry[]"),
            new ParameterInfo("arg1", "String...", "String[]"));

    @Test
    void renamesParametersAndReRendersTheSignature() {
        MemberDescriptor named = ParameterNames.apply(JOIN, List.of("entries", "separators"));

        assertThat(named.signature()).isEqualTo(
                "static <T> String join(Map.Entry<T, String>[] entries, String... separators) throws IOException");
        assertThat(named.parameters()).containsExactly(
                new ParameterInfo("entries", "Map.Entry<T, String>[]", "Map.Entry[]"),
                new ParameterInfo("separators", "String...", "String[]"));
        assertThat(named.erasedKey()).isEqualTo(JOIN.erasedKey());
    }

    @Test
    void renamesConstructorParameters() {
        MemberDescriptor constructor = member(MemberKind.CONSTRUCTOR, "Util", "protected <T> Util(T arg0)",
                new ParameterInfo("arg0", "T", "Object"));

        assertThat(ParameterNames.apply(constructor, List.of("seed")).signature())
                .isEqualTo("protected <T> Util(T seed)");
        assertThat(ParameterNames.apply(member(MemberKind.CONSTRUCTOR, "Util", "Util(int arg0)",
                new ParameterInfo("arg0", "int", "int")), List.of("size")).signature()).isEqualTo("Util(int size)");
    }

    @Test
    void keepsTheMemberWhenTheNamesCannotBeApplied() {
        assertThat(ParameterNames.apply(JOIN, List.of("entries"))).isSameAs(JOIN);
        assertThat(ParameterNames.apply(JOIN, List.of("entries", "1st"))).isSameAs(JOIN);
        assertThat(ParameterNames.apply(JOIN, List.of("same", "same"))).isSameAs(JOIN);
        assertThat(ParameterNames.apply(JOIN, List.of("arg0", "arg1"))).isSameAs(JOIN);
        MemberDescriptor inconsistent = JOIN.toBuilder().signature("String join(Object a, Object b)").build();
        assertThat(ParameterNames.apply(inconsistent, List.of("entries", "separators"))).isSameAs(inconsistent);
        MemberDescriptor partOfAnotherName = member(MemberKind.METHOD, "in", "void join(int arg0)",
                new ParameterInfo("arg0", "int", "int"));
        assertThat(ParameterNames.apply(partOfAnotherName, List.of("x"))).isSameAs(partOfAnotherName);
    }
}
