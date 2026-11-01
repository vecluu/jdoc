package io.github.vecluu.jdoc.bytecode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.bytecode.JvmType.ArrayType;
import io.github.vecluu.jdoc.bytecode.JvmType.ClassSignature;
import io.github.vecluu.jdoc.bytecode.JvmType.ClassType;
import io.github.vecluu.jdoc.bytecode.JvmType.MethodType;
import io.github.vecluu.jdoc.bytecode.JvmType.Primitive;
import io.github.vecluu.jdoc.bytecode.JvmType.TypeVariable;
import io.github.vecluu.jdoc.bytecode.JvmType.Variance;
import org.junit.jupiter.api.Test;

class SignatureParserTest {

    private final SignatureParser parser = new SignatureParser();

    @Test
    void parsesFieldDescriptors() {
        assertThat(parser.parseFieldType("I")).isEqualTo(new Primitive("int"));
        assertThat(parser.parseFieldType("[[J")).isEqualTo(new ArrayType(new ArrayType(new Primitive("long"))));
        assertThat(parser.parseFieldType("Ljava/lang/String;")).isEqualTo(ClassType.of("java/lang/String"));
    }

    @Test
    void parsesMethodDescriptor() {
        MethodType method = parser.parseMethod("(ILjava/lang/String;[D)V");

        assertThat(method.typeParameters()).isEmpty();
        assertThat(method.parameters()).containsExactly(new Primitive("int"), ClassType.of("java/lang/String"),
                new ArrayType(new Primitive("double")));
        assertThat(method.returnType()).isEqualTo(new Primitive("void"));
        assertThat(method.throwsTypes()).isEmpty();
    }

    @Test
    void parsesGenericMethodSignatureWithBoundsAndThrows() {
        MethodType method = parser.parseMethod(
                "<T::Ljava/lang/Comparable<-TT;>;X:Ljava/lang/Exception;>(TT;[TT;)TT;^TX;^Ljava/io/IOException;");

        assertThat(method.typeParameters()).hasSize(2);
        assertThat(method.typeParameters().get(0).name()).isEqualTo("T");
        assertThat(method.typeParameters().get(0).classBound()).isEmpty();
        assertThat(method.typeParameters().get(0).interfaceBounds()).hasSize(1);
        assertThat(method.typeParameters().get(1).classBound()).contains(ClassType.of("java/lang/Exception"));
        assertThat(method.parameters()).containsExactly(new TypeVariable("T"), new ArrayType(new TypeVariable("T")));
        assertThat(method.throwsTypes()).containsExactly(new TypeVariable("X"), ClassType.of("java/io/IOException"));
    }

    @Test
    void parsesWildcardsAndInnerClassesOfGenericTypes() {
        ClassType type = (ClassType) parser.parseFieldType(
                "Lp/Outer<TT;>.Inner<*+Ljava/lang/Number;-Ljava/lang/Integer;>;");

        assertThat(type.internalName()).isEqualTo("p/Outer$Inner");
        assertThat(type.segments()).hasSize(2);
        assertThat(type.segments().get(1).arguments()).extracting(JvmType.TypeArgument::variance)
                .containsExactly(Variance.UNBOUNDED, Variance.EXTENDS, Variance.SUPER);
    }

    @Test
    void parsesClassSignature() {
        ClassSignature signature = parser.parseClass(
                "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/util/AbstractMap<TK;TV;>;Ljava/io/Serializable;");

        assertThat(signature.typeParameters()).extracting(JvmType.TypeParameter::name).containsExactly("K", "V");
        assertThat(signature.superclass().internalName()).isEqualTo("java/util/AbstractMap");
        assertThat(signature.interfaces()).containsExactly(ClassType.of("java/io/Serializable"));
    }

    @Test
    void rejectsMalformedSignatures() {
        assertThatThrownBy(() -> parser.parseFieldType("Ljava/lang/String")).isInstanceOf(
                IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseFieldType("Q")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseMethod("(I")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseFieldType("II")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseFieldType("")).isInstanceOf(IllegalArgumentException.class);
    }
}
