package io.github.vecluu.jdoc.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.IndexedMember;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassViewServiceTest {

    @TempDir
    Path tmp;

    private LookupIndex index;
    private LookupServices services;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        SampleSources.addTo(index);
        services = index.services();
    }

    @AfterEach
    void tearDown() {
        services.close();
        index.close();
    }

    @Test
    void listsOwnMembersInJavadocGroupOrder() {
        ClassView view = view("a.Impl");

        assertThat(view.members()).extracting(member -> member.descriptor().signature())
                .containsExactly("static final int LIMIT", "Impl()", "int size(int scale)", "void own()",
                        "String toString()");
        assertThat(view.firstParagraph()).isEqualTo("A concrete implementation.");
        assertThat(view.directSupertypes()).extracting(supertype -> supertype.name().binaryName())
                .containsExactly("a.Base");
        assertThat(view.nestedTypes()).extracting(type -> type.name().binaryName()).containsExactly("a.Impl$Nested");
    }

    @Test
    void groupsInheritedNamesByDeclaringTypeWithoutOverriddenOrHiddenMembers() {
        ClassView view = view("a.Impl");

        assertThat(view.inherited()).extracting(group -> group.declaringType().name().simpleName())
                .containsExactly("Base", "Named", "Object");
        assertThat(view.inherited().get(0).names()).containsExactly("name", "size", "helper");
        assertThat(view.inherited().get(1).names()).containsExactly("compareTo");
        assertThat(view.inherited().get(2).names()).containsExactly("equals", "getClass", "hashCode");
    }

    @Test
    void firstParagraphStopsAtTheFirstBlankLine() {
        ClassView view = view("a.Base");

        assertThat(view.firstParagraph()).isEqualTo("Base class for named things. Second sentence.");
        assertThat(view.members()).extracting(IndexedMember::descriptor)
                .extracting(member -> member.name())
                .containsExactly("LIMIT", "Base", "name", "size", "size", "helper");
    }

    @Test
    void interfacesDoNotInheritFromObjectAndStaticInterfaceMethodsAreNotInherited() {
        ClassView named = view("a.Named");
        ClassView other = view("a.Other");

        assertThat(named.inherited()).isEmpty();
        assertThat(other.inherited()).extracting(group -> group.declaringType().name().simpleName())
                .containsExactly("Named", "Object");
        assertThat(other.inherited().getFirst().names()).containsExactly("compareTo");
    }

    @Test
    void recordsListTheirImplicitMembers() {
        ClassView view = view("a.Point");

        assertThat(view.members()).extracting(member -> member.descriptor().name()).contains("Point", "x");
        assertThat(view.firstParagraph()).isEqualTo("A point.");
    }

    private ClassView view(String binaryName) {
        TypeLookup.Found found = (TypeLookup.Found) services.typeResolver().resolve(binaryName, index.scope());
        return services.classView().view(found.type(), index.scope());
    }
}
