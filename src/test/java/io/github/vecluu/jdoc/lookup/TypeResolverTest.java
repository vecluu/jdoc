package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.lookup.LookupIndex.publicClass;
import static io.github.vecluu.jdoc.lookup.LookupIndex.publicInterface;
import static io.github.vecluu.jdoc.lookup.LookupIndex.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.Visibility;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeResolverTest {

    @TempDir
    Path tmp;

    private LookupIndex index;
    private TypeResolver resolver;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        index.add(LookupIndex.artifact(ArtifactKey.jdk(25)), List.of(
                publicInterface("java.util.Map"),
                type("java.util.Map$Entry", TypeKind.INTERFACE, Visibility.PUBLIC),
                publicClass("java.time.Duration"),
                publicClass("javax.xml.datatype.Duration"),
                publicClass("java.util.Optional"),
                type("java.util.Hidden", TypeKind.CLASS, Visibility.PACKAGE),
                type("java.util.Hidden$Inner", TypeKind.CLASS, Visibility.PUBLIC)), List.of());
        index.add("com.acme:lib:1.0", List.of(
                publicClass("com.acme.Outer"),
                publicClass("com.acme.Outer$Inner"),
                publicClass("com.acme.Outer$Inner$Deep"),
                publicClass("com.acme.Shared")));
        index.add("com.other:lib:2.0", List.of(publicClass("com.acme.Shared")));
        resolver = new TypeResolver(new ScopedTypes(index.types));
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void resolvesFullyQualifiedNames() {
        assertFound("java.util.Optional", "java.util.Optional");
        assertFound("java.util.Map.Entry", "java.util.Map$Entry");
        assertFound("java.util.Map$Entry", "java.util.Map$Entry");
        assertFound("com.acme.Outer.Inner.Deep", "com.acme.Outer$Inner$Deep");
        assertFound("java.util.Optional<T>", "java.util.Optional");
    }

    @Test
    void resolvesSimpleAndNestedNames() {
        assertFound("Optional", "java.util.Optional");
        assertFound("Outer.Inner", "com.acme.Outer$Inner");
        assertFound("Outer$Inner", "com.acme.Outer$Inner");
        assertFound("Inner.Deep", "com.acme.Outer$Inner$Deep");
        assertFound("Entry", "java.util.Map$Entry");
        assertFound("Map.Entry", "java.util.Map$Entry");
    }

    @Test
    void reportsAmbiguousSimpleNamesWithCandidatesAndLabels() {
        TypeLookup lookup = resolver.resolve("Duration", index.scope());

        assertThat(lookup).isInstanceOf(TypeLookup.Ambiguous.class);
        List<LocatedType> candidates = ((TypeLookup.Ambiguous) lookup).candidates();
        assertThat(candidates).extracting(type -> type.name().displayName())
                .containsExactly("java.time.Duration", "javax.xml.datatype.Duration");
        assertThat(candidates).extracting(LocatedType::label).containsOnly("jdk 25");
    }

    @Test
    void sameNameInTwoArtifactsIsNotAmbiguousFirstInClasspathOrderWins() {
        TypeLookup lookup = resolver.resolve("Shared", index.scope());

        assertThat(lookup).isInstanceOf(TypeLookup.Found.class);
        TypeLookup.Found found = (TypeLookup.Found) lookup;
        assertThat(found.type().label()).isEqualTo("lib 1.0");
        assertThat(found.shadowed()).extracting(LocatedType::label).containsExactly("lib 2.0");
        assertThat(((TypeLookup.Found) resolver.resolve("com.acme.Shared", index.scope())).type().label())
                .isEqualTo("lib 1.0");
    }

    @Test
    void ignoresTypesThatAreNotApiVisible() {
        assertThat(resolver.resolve("java.util.Hidden", index.scope())).isInstanceOf(TypeLookup.NotFound.class);
        assertThat(resolver.resolve("Hidden.Inner", index.scope())).isInstanceOf(TypeLookup.NotFound.class);
        assertThat(resolver.resolve("Inner", index.scope()))
                .isInstanceOfSatisfying(TypeLookup.Found.class,
                        found -> assertThat(found.type().name().binaryName()).isEqualTo("com.acme.Outer$Inner"));
    }

    @Test
    void notFoundSuggestsTypesDifferingOnlyInCase() {
        TypeLookup lookup = resolver.resolve("optional", index.scope());

        assertThat(lookup).isInstanceOfSatisfying(TypeLookup.NotFound.class, notFound -> {
            assertThat(notFound.input()).isEqualTo("optional");
            assertThat(notFound.suggestions()).extracting(type -> type.name().binaryName())
                    .containsExactly("java.util.Optional");
        });
        assertThat(resolver.resolve("Nope", index.scope()))
                .isInstanceOfSatisfying(TypeLookup.NotFound.class,
                        notFound -> assertThat(notFound.suggestions()).isEmpty());
    }

    @Test
    void scopeLimitsTheArtifactsSearched() {
        LookupScope jdkOnly = LookupScope.of(index.scope().artifacts().subList(0, 1));

        assertThat(resolver.resolve("Outer", jdkOnly)).isInstanceOf(TypeLookup.NotFound.class);
        assertThat(resolver.resolve("Outer", LookupScope.of(List.of()))).isInstanceOf(TypeLookup.NotFound.class);
    }

    @Test
    void invalidInputIsAValue() {
        assertThat(resolver.resolve("  ", index.scope())).isInstanceOf(TypeLookup.Invalid.class);
        assertThat(resolver.resolve("java..util", index.scope())).isInstanceOf(TypeLookup.Invalid.class);
    }

    private void assertFound(String input, String binaryName) {
        assertThat(resolver.resolve(input, index.scope()))
                .as(input)
                .isInstanceOfSatisfying(TypeLookup.Found.class,
                        found -> assertThat(found.type().name().binaryName()).isEqualTo(binaryName));
    }
}
