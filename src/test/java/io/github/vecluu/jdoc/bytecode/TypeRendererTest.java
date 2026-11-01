package io.github.vecluu.jdoc.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TypeRendererTest {

    private final SignatureParser parser = new SignatureParser();
    private final TypeRenderer renderer = new TypeRenderer();

    private String render(String signature) {
        return renderer.render(parser.parseFieldType(signature));
    }

    private String erased(String descriptor) {
        return renderer.renderErased(parser.parseFieldType(descriptor));
    }

    @Test
    void rendersSimpleNamesForTopLevelAndRelativeNamesForNestedTypes() {
        assertThat(render("Ljava/util/List<Ljava/lang/String;>;")).isEqualTo("List<String>");
        assertThat(render("Ljava/util/Map$Entry<TK;TV;>;")).isEqualTo("Map.Entry<K, V>");
        assertThat(render("Lp/A$B$C;")).isEqualTo("A.B.C");
        assertThat(render("LTop;")).isEqualTo("Top");
    }

    @Test
    void rendersNestedGenericsWildcardsAndArrays() {
        assertThat(render("Ljava/util/Map<Ljava/lang/String;Ljava/util/List<+Ljava/lang/Number;>;>;"))
                .isEqualTo("Map<String, List<? extends Number>>");
        assertThat(render("Ljava/util/Comparator<-TT;>;")).isEqualTo("Comparator<? super T>");
        assertThat(render("Ljava/lang/Class<*>;")).isEqualTo("Class<?>");
        assertThat(render("[[Ljava/util/List<TT;>;")).isEqualTo("List<T>[][]");
        assertThat(render("[TT;")).isEqualTo("T[]");
        assertThat(render("Z")).isEqualTo("boolean");
    }

    @Test
    void rendersInnerClassesOfGenericClassesAsChain() {
        assertThat(render("Lp/Outer<TT;>.Inner<Ljava/lang/String;>;")).isEqualTo("Outer<T>.Inner<String>");
        assertThat(render("Lp/Outer<TT;>.Mid.Inner;")).isEqualTo("Outer<T>.Mid.Inner");
    }

    @Test
    void erasesToSimpleNamesWithoutArguments() {
        assertThat(erased("Ljava/util/Map$Entry;")).isEqualTo("Map.Entry");
        assertThat(erased("[Ljava/lang/Object;")).isEqualTo("Object[]");
        assertThat(erased("[[I")).isEqualTo("int[][]");
        assertThat(renderer.renderErased(parser.parseFieldType("Lp/Outer<TT;>.Inner<TU;>;"))).isEqualTo(
                "Outer.Inner");
    }

    @Test
    void rendersTypeParametersWithBounds() {
        var method = parser.parseMethod(
                "<K:Ljava/lang/Object;V::Ljava/lang/Comparable<-TV;>;E:Ljava/lang/Number;:Ljava/io/Serializable;>()V");

        assertThat(renderer.renderTypeParameters(method.typeParameters()))
                .isEqualTo("<K, V extends Comparable<? super V>, E extends Number & Serializable>");
        assertThat(renderer.renderTypeParameters(List.of())).isEmpty();
    }
}
