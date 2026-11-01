package io.github.vecluu.jdoc.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.lookup.Supertype;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClassViewFormatterTest {

    private static TypeDescriptor type(TypeKind kind) {
        return TypeDescriptor.builder().name(TypeName.of("a.T")).kind(kind).visibility(Visibility.PUBLIC)
                .docSource(DocSource.SOURCES).entryPath("a/T.java").build();
    }

    private static List<Supertype> supertypes(String... names) {
        return Arrays.stream(names).map(name -> new Supertype(TypeName.of(name), Optional.empty())).toList();
    }

    @Test
    void classWithSuperclassAndInterfaces() {
        assertThat(ClassViewFormatter.supertypesLine(type(TypeKind.CLASS),
                supertypes("java.util.AbstractList", "java.util.List", "java.util.Map$Entry")))
                .isEqualTo("extends java.util.AbstractList  implements java.util.List, java.util.Map.Entry");
        assertThat(ClassViewFormatter.supertypesLine(type(TypeKind.CLASS), supertypes("a.Base")))
                .isEqualTo("extends a.Base");
    }

    @Test
    void impliedSupertypesAreLeftOut() {
        assertThat(ClassViewFormatter.supertypesLine(type(TypeKind.CLASS), supertypes("java.lang.Object")))
                .isEmpty();
        assertThat(ClassViewFormatter.supertypesLine(type(TypeKind.CLASS),
                supertypes("java.lang.Object", "java.io.Serializable"))).isEqualTo("implements java.io.Serializable");
        assertThat(ClassViewFormatter.supertypesLine(type(TypeKind.ENUM),
                supertypes("java.lang.Enum", "a.Named"))).isEqualTo("implements a.Named");
        assertThat(ClassViewFormatter.supertypesLine(type(TypeKind.RECORD), supertypes("java.lang.Record")))
                .isEmpty();
        assertThat(ClassViewFormatter.supertypesLine(type(TypeKind.ANNOTATION),
                supertypes("java.lang.annotation.Annotation"))).isEmpty();
    }

    @Test
    void interfacesExtend() {
        assertThat(ClassViewFormatter.supertypesLine(type(TypeKind.INTERFACE),
                supertypes("java.util.Collection", "java.util.SequencedCollection")))
                .isEqualTo("extends java.util.Collection, java.util.SequencedCollection");
    }
}
