package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.lookup.LookupIndex.publicClass;
import static io.github.vecluu.jdoc.lookup.LookupIndex.publicInterface;
import static io.github.vecluu.jdoc.lookup.LookupIndex.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TypeHierarchyTest {

    @TempDir
    Path tmp;

    private LookupIndex index;
    private ScopedTypes types;
    private TypeHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        index.add(LookupIndex.artifact(ArtifactKey.jdk(25)), List.of(
                publicClass("java.lang.Object"),
                publicInterface("java.lang.Comparable")), List.of());
        index.add("com.acme:lib:1.0", List.of(
                publicClass("a.A", "java.lang.Object", "a.I3"),
                publicClass("a.B", "a.A", "a.I2"),
                publicClass("a.C", "a.B", "a.I1", "java.lang.Comparable"),
                publicInterface("a.I1", "a.I3"),
                publicInterface("a.I2", "x.Unknown"),
                publicInterface("a.I3"),
                type("a.Base", TypeKind.CLASS, Visibility.PACKAGE, "java.lang.Object", "a.I1"),
                publicClass("a.Impl", "a.Base"),
                publicClass("a.D", "x.Missing")));
        index.add("com.other:ext:1.0", List.of(
                publicClass("b.FromOther", "a.C"),
                type("b.Private", TypeKind.CLASS, Visibility.PRIVATE, "a.C")));
        types = new ScopedTypes(index.types);
        hierarchy = new TypeHierarchy(types, index.supertypes);
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void supertypesComeInJavadocOrderSuperclassChainThenInterfacesThenObject() {
        List<Supertype> supertypes = hierarchy.supertypes(located("a.C"), index.scope());

        assertThat(supertypes).extracting(supertype -> supertype.name().binaryName())
                .containsExactly("a.B", "a.A", "a.I1", "java.lang.Comparable", "a.I2", "a.I3", "x.Unknown",
                        "java.lang.Object");
        assertThat(supertypes).filteredOn(supertype -> !supertype.isKnown())
                .extracting(supertype -> supertype.name().binaryName())
                .containsExactly("x.Unknown");
    }

    @Test
    void interfacesHaveNoObjectAndUnknownSuperclassesStopTheChain() {
        assertThat(hierarchy.supertypes(located("a.I1"), index.scope()))
                .extracting(supertype -> supertype.name().binaryName())
                .containsExactly("a.I3");
        assertThat(hierarchy.supertypes(located("a.D"), index.scope()))
                .extracting(supertype -> supertype.name().binaryName())
                .containsExactly("x.Missing");
        assertThat(hierarchy.supertypes(located("java.lang.Object"), index.scope())).isEmpty();
    }

    @Test
    void directSupertypesKeepDeclarationOrder() {
        assertThat(hierarchy.directSupertypes(located("a.C"), index.scope()))
                .extracting(supertype -> supertype.name().binaryName())
                .containsExactly("a.B", "a.I1", "java.lang.Comparable");
    }

    @Test
    void subtypesAreTransitiveAcrossArtifactsAndSkipNonApiTypes() {
        List<Subtype> subtypes = hierarchy.subtypes(located("a.I1"), index.scope());

        assertThat(subtypes).extracting(subtype -> subtype.type().name().binaryName())
                .containsExactly("a.C", "a.Impl", "b.FromOther");
        assertThat(subtypes).extracting(Subtype::direct).containsExactly(true, false, false);
        assertThat(subtypes.getLast().type().label()).isEqualTo("ext 1.0");
    }

    @Test
    void subtypesOutsideTheScopeAreNotListed() {
        LookupScope withoutExt = LookupScope.of(index.scope().artifacts().subList(0, 2));

        assertThat(hierarchy.subtypes(located("a.C"), withoutExt)).isEmpty();
        assertThat(hierarchy.subtypes(located("a.C"), index.scope()))
                .extracting(subtype -> subtype.type().name().binaryName())
                .containsExactly("b.FromOther");
    }

    private LocatedType located(String binaryName) {
        return types.first(TypeName.of(binaryName), index.scope()).orElseThrow();
    }
}
