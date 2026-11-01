package io.github.vecluu.jdoc.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TypeNameTest {

    @Test
    void topLevelType() {
        TypeName optional = TypeName.of("java.util.Optional");
        assertThat(optional.packageName()).isEqualTo("java.util");
        assertThat(optional.simpleName()).isEqualTo("Optional");
        assertThat(optional.simpleNames()).containsExactly("Optional");
        assertThat(optional.displayName()).isEqualTo("java.util.Optional");
        assertThat(optional.relativeName()).isEqualTo("Optional");
        assertThat(optional.outerName()).isEmpty();
        assertThat(optional.topLevel()).isSameAs(optional);
        assertThat(optional.isNested()).isFalse();
        assertThat(optional.nestingDepth()).isZero();
        assertThat(optional.sourceEntryPath()).isEqualTo("java/util/Optional.java");
        assertThat(optional.classEntryPath()).isEqualTo("java/util/Optional.class");
        assertThat(optional.javadocPagePath()).isEqualTo("java/util/Optional.html");
        assertThat(optional).hasToString("java.util.Optional");
    }

    @Test
    void nestedType() {
        TypeName entry = TypeName.of("java.util.Map$Entry");
        assertThat(entry.packageName()).isEqualTo("java.util");
        assertThat(entry.simpleName()).isEqualTo("Entry");
        assertThat(entry.displayName()).isEqualTo("java.util.Map.Entry");
        assertThat(entry.relativeName()).isEqualTo("Map.Entry");
        assertThat(entry.outerName()).contains(TypeName.of("java.util.Map"));
        assertThat(entry.topLevel()).isEqualTo(TypeName.of("java.util.Map"));
        assertThat(entry.sourceEntryPath()).isEqualTo("java/util/Map.java");
        assertThat(entry.classEntryPath()).isEqualTo("java/util/Map$Entry.class");
        assertThat(entry.javadocPagePath()).isEqualTo("java/util/Map.Entry.html");
        assertThat(entry.internalName()).isEqualTo("java/util/Map$Entry");
    }

    @Test
    void deeplyNestedType() {
        TypeName deeper = TypeName.of("a.b.Outer$Inner$Deeper");
        assertThat(deeper.simpleNames()).containsExactly("Outer", "Inner", "Deeper");
        assertThat(deeper.nestingDepth()).isEqualTo(2);
        assertThat(deeper.outerName()).contains(TypeName.of("a.b.Outer$Inner"));
        assertThat(deeper.outerName().flatMap(TypeName::outerName)).contains(TypeName.of("a.b.Outer"));
        assertThat(deeper.topLevel()).isEqualTo(TypeName.of("a.b.Outer"));
        assertThat(deeper.displayName()).isEqualTo("a.b.Outer.Inner.Deeper");
        assertThat(deeper.sourceEntryPath()).isEqualTo("a/b/Outer.java");
        assertThat(deeper.javadocPagePath()).isEqualTo("a/b/Outer.Inner.Deeper.html");
    }

    @Test
    void defaultPackage() {
        TypeName type = TypeName.of("Foo$Bar");
        assertThat(type.packageName()).isEmpty();
        assertThat(type.isInDefaultPackage()).isTrue();
        assertThat(type.packagePath()).isEmpty();
        assertThat(type.displayName()).isEqualTo("Foo.Bar");
        assertThat(type.sourceEntryPath()).isEqualTo("Foo.java");
        assertThat(type.classEntryPath()).isEqualTo("Foo$Bar.class");
        assertThat(type.javadocPagePath()).isEqualTo("Foo.Bar.html");
        assertThat(TypeName.of("", List.of("Foo"))).isEqualTo(TypeName.of("Foo"));
    }

    @Test
    void dollarSignsAtEdgesStayInTheIdentifier() {
        assertThat(TypeName.of("p.$Proxy").simpleNames()).containsExactly("$Proxy");
        assertThat(TypeName.of("p.Foo$").simpleNames()).containsExactly("Foo$");
        assertThat(TypeName.of("p.A$$B").simpleNames()).containsExactly("A$$B");
        assertThat(TypeName.of("p.$Gson$Types").simpleNames()).containsExactly("$Gson", "Types");
    }

    @Test
    void factoryFromPackageAndSimpleNames() {
        assertThat(TypeName.of("java.util", List.of("Map", "Entry"))).isEqualTo(TypeName.of("java.util.Map$Entry"));
        assertThatThrownBy(() -> TypeName.of("java.util", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TypeName.of("java.util", List.of("Map.Entry")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factoryFromDisplayNameWithNestingDepth() {
        assertThat(TypeName.fromDisplayName("java.util.Map.Entry", 1)).isEqualTo(TypeName.of("java.util.Map$Entry"));
        assertThat(TypeName.fromDisplayName("java.util.Map", 0)).isEqualTo(TypeName.of("java.util.Map"));
        assertThat(TypeName.fromDisplayName("Outer.Inner", 1)).isEqualTo(TypeName.of("Outer$Inner"));
        assertThatThrownBy(() -> TypeName.fromDisplayName("Outer.Inner", 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TypeName.fromDisplayName("a.B", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factoriesFromInternalNameAndClassEntry() {
        assertThat(TypeName.fromInternalName("java/util/Map$Entry")).isEqualTo(TypeName.of("java.util.Map$Entry"));
        assertThat(TypeName.fromClassEntryPath("java/util/Map$Entry.class"))
                .isEqualTo(TypeName.of("java.util.Map$Entry"));
        assertThatThrownBy(() -> TypeName.fromClassEntryPath("java/util/Map.java"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nestedChild() {
        assertThat(TypeName.of("a.Outer").nested("Inner")).isEqualTo(TypeName.of("a.Outer$Inner"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "java/util/Map", "a..B", ".A", "A.", "a.B C"})
    void rejectsInvalidBinaryNames(String name) {
        assertThatThrownBy(() -> TypeName.of(name)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordersByBinaryName() {
        assertThat(List.of(TypeName.of("b.A"), TypeName.of("a.Z")).stream().sorted())
                .containsExactly(TypeName.of("a.Z"), TypeName.of("b.A"));
    }
}
