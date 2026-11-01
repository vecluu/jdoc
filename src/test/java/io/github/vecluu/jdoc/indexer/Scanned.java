package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.List;

/** Builds {@link ScannedClass}es as the bytecode scanner returns them (DECOMPILED, class entry path). */
final class Scanned {

    private Scanned() {
    }

    static ScannedClass type(String binaryName, List<TypeName> supertypes, String... methods) {
        TypeName name = TypeName.of(binaryName);
        TypeDescriptor type = TypeDescriptor.builder()
                .name(name)
                .kind(TypeKind.CLASS)
                .visibility(Visibility.PUBLIC)
                .modifiers("public")
                .supertypes(supertypes)
                .docSource(DocSource.DECOMPILED)
                .entryPath(name.classEntryPath())
                .build();
        List<MemberDescriptor> members = java.util.Arrays.stream(methods)
                .map(method -> MemberDescriptor.builder()
                        .declaringType(name)
                        .name(method)
                        .kind(MemberKind.METHOD)
                        .signature("void " + method + "()")
                        .visibility(Visibility.PUBLIC)
                        .build())
                .toList();
        return new ScannedClass(type, members);
    }

    static ScannedClass type(String binaryName, String... methods) {
        return type(binaryName, List.of(TypeName.of("java.lang.Object")), methods);
    }
}
