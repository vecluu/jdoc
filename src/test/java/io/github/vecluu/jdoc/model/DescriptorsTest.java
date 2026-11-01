package io.github.vecluu.jdoc.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DescriptorsTest {

    private static final TypeName OPTIONAL = TypeName.of("java.util.Optional");

    private static MemberDescriptor.MemberDescriptorBuilder method() {
        return MemberDescriptor.builder()
                .declaringType(OPTIONAL)
                .name("of")
                .kind(MemberKind.METHOD)
                .signature("static <T> Optional<T> of(T value)")
                .parameters(List.of(new ParameterInfo("value", "T", "Object")))
                .visibility(Visibility.PUBLIC)
                .isStatic(true);
    }

    @Test
    void memberDefaultsAndDerivedKeys() {
        MemberDescriptor member = method().build();
        assertThat(member.firstSentence()).isEmpty();
        assertThat(member.range()).isEmpty();
        assertThat(member.docRange()).isEmpty();
        assertThat(member.arity()).isEqualTo(1);
        assertThat(member.erasedParameterTypes()).containsExactly("Object");
        assertThat(member.erasedKey()).isEqualTo("of(Object)");
        assertThat(member.reference()).isEqualTo("java.util.Optional#of(Object)");
    }

    @Test
    void memberCopiesParametersDefensively() {
        List<ParameterInfo> params = new ArrayList<>(List.of(new ParameterInfo("a", "int", "int")));
        MemberDescriptor member = method().parameters(params).build();
        params.clear();
        assertThat(member.parameters()).hasSize(1);
        assertThatThrownBy(() -> member.parameters().add(new ParameterInfo("b", "int", "int")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fieldKeyHasNoParentheses() {
        MemberDescriptor field = method().name("EMPTY").kind(MemberKind.FIELD).signature("static final Optional<?> EMPTY")
                .parameters(null).build();
        assertThat(field.erasedKey()).isEqualTo("EMPTY");
        assertThat(field.reference()).isEqualTo("java.util.Optional#EMPTY");
    }

    @Test
    void constructorUsesSimpleTypeName() {
        TypeName entry = TypeName.of("a.Outer$Entry");
        MemberDescriptor ctor = method().declaringType(entry).name("Entry").kind(MemberKind.CONSTRUCTOR)
                .signature("Entry()").parameters(List.of()).isStatic(false).build();
        assertThat(ctor.erasedKey()).isEqualTo("Entry()");
        assertThatThrownBy(() -> method().name("<init>").kind(MemberKind.CONSTRUCTOR).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fieldsCannotHaveParameters() {
        assertThatThrownBy(() -> method().kind(MemberKind.ENUM_CONSTANT).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void memberRequiresCoreFields() {
        assertThatThrownBy(() -> method().signature(" ").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> method().visibility(null).build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void parameterInfo() {
        ParameterInfo varargs = new ParameterInfo("names", "String...", "String[]");
        assertThat(varargs.isVarargs()).isTrue();
        assertThat(varargs.declaration()).isEqualTo("String... names");
        assertThat(new ParameterInfo("list", "List<String>", "List").isVarargs()).isFalse();
        assertThatThrownBy(() -> new ParameterInfo("x", "", "int")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void typeDescriptorDefaults() {
        TypeDescriptor type = TypeDescriptor.builder()
                .name(OPTIONAL)
                .kind(TypeKind.CLASS)
                .visibility(Visibility.PUBLIC)
                .docSource(DocSource.JDK)
                .entryPath("java/util/Optional.java")
                .build();
        assertThat(type.modifiers()).isEmpty();
        assertThat(type.typeParameters()).isEmpty();
        assertThat(type.supertypes()).isEmpty();
        assertThat(type.firstSentence()).isEmpty();
        assertThat(type.declarationRange()).isEmpty();
        TypeDescriptor copy = type.toBuilder().declarationRange(Optional.of(new SourceRange(3, 9))).build();
        assertThat(copy.declarationRange()).contains(new SourceRange(3, 9));
        assertThatThrownBy(() -> type.toBuilder().entryPath("").build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indexedRowsExposeDescriptor() {
        MemberDescriptor member = method().build();
        assertThat(new IndexedMember(7, 3, member).descriptor()).isSameAs(member);
        TypeDescriptor type = TypeDescriptor.builder().name(OPTIONAL).kind(TypeKind.CLASS)
                .visibility(Visibility.PUBLIC).docSource(DocSource.JDK).entryPath("java/util/Optional.java").build();
        assertThat(new IndexedType(1, 2, type).name()).isEqualTo(OPTIONAL);
    }
}
