package io.github.vecluu.jdoc.source;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TypeNameResolverTest {

    private static final TypeName OUTER = TypeName.of("p.Outer");
    private static final TypeName INNER = TypeName.of("p.Outer$Inner");
    private static final TypeName DEEP = TypeName.of("p.Outer$Inner$Deep");

    private TypeNameResolver resolver(KnownTypes known) {
        return new TypeNameResolver("p", Set.of(OUTER, INNER, DEEP, TypeName.of("p.Outer$Entry")),
                List.of("java.util.Map", "java.util.Map.Entry", "a.b.Thing"),
                List.of("x.y", "java.util.concurrent"), known);
    }

    private String resolve(String written, TypeName enclosing) {
        return resolver(KnownTypes.none()).resolve(written, Optional.ofNullable(enclosing)).binaryName();
    }

    @Test
    void sameFileTypesFromInnermostScope() {
        assertThat(resolve("Deep", INNER)).isEqualTo("p.Outer$Inner$Deep");
        assertThat(resolve("Inner", DEEP)).isEqualTo("p.Outer$Inner");
        assertThat(resolve("Outer", DEEP)).isEqualTo("p.Outer");
        assertThat(resolve("Entry", OUTER)).isEqualTo("p.Outer$Entry");
        assertThat(resolve("Inner.Deep", null)).isEqualTo("x.y.Inner$Deep"); // not in scope at file level
        assertThat(resolve("Outer.Inner.Deep", null)).isEqualTo("p.Outer$Inner$Deep");
    }

    @Test
    void singleTypeImports() {
        assertThat(resolve("Entry", null)).isEqualTo("java.util.Map$Entry");
        assertThat(resolve("Map.Entry", null)).isEqualTo("java.util.Map$Entry");
        assertThat(resolve("Thing", null)).isEqualTo("a.b.Thing");
    }

    @Test
    void javaLangBeforeOnDemandGuess() {
        assertThat(resolve("String", null)).isEqualTo("java.lang.String");
        assertThat(resolve("Thread.State", null)).isEqualTo("java.lang.Thread$State");
    }

    @Test
    void unknownNamesGuessFirstOnDemandImport() {
        assertThat(resolve("Unknown", null)).isEqualTo("x.y.Unknown");
    }

    @Test
    void unknownNamesWithoutOnDemandImportsAreSamePackage() {
        TypeNameResolver plain = new TypeNameResolver("p", Set.of(), List.of(), List.of(), KnownTypes.none());
        assertThat(plain.resolve("Unknown", Optional.empty()).binaryName()).isEqualTo("p.Unknown");
        TypeNameResolver defaultPackage = new TypeNameResolver("", Set.of(), List.of(), List.of(),
                KnownTypes.none());
        assertThat(defaultPackage.resolve("Unknown", Optional.empty()).binaryName()).isEqualTo("Unknown");
    }

    @Test
    void knownTypesDecideSamePackageAndOnDemand() {
        TypeNameResolver known = resolver(KnownTypes.of(List.of(
                TypeName.of("p.Helper"), TypeName.of("java.util.concurrent.Callable"))));
        assertThat(known.resolve("Helper", Optional.empty()).binaryName()).isEqualTo("p.Helper");
        assertThat(known.resolve("Callable", Optional.empty()).binaryName())
                .isEqualTo("java.util.concurrent.Callable");
    }

    @Test
    void qualifiedNames() {
        assertThat(resolve("java.util.Map.Entry", OUTER)).isEqualTo("java.util.Map$Entry");
        assertThat(resolve("java.io.Closeable", null)).isEqualTo("java.io.Closeable");
        assertThat(TypeNameResolver.qualified("a.b.c").binaryName()).isEqualTo("a.b.c");
        assertThat(TypeNameResolver.qualified("Top").binaryName()).isEqualTo("Top");
    }
}
