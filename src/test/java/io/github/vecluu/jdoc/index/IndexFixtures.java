package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Builders for index rows used by the index tests. */
final class IndexFixtures {

    private IndexFixtures() {
    }

    static IndexedArtifact artifact(String gav) {
        Gav parsed = Gav.parse(gav);
        return IndexedArtifact.builder()
                .key(ArtifactKey.maven(parsed))
                .jarPath(Path.of("/repo").resolve(parsed.repositoryPath()))
                .jarMtime(1_700_000_000_123L)
                .jarSize(4096)
                .indexedAt(Instant.parse("2026-01-02T03:04:05.123456789Z"))
                .build();
    }

    static TypeDescriptor type(String binaryName, TypeName... supertypes) {
        TypeName name = TypeName.of(binaryName);
        return TypeDescriptor.builder()
                .name(name)
                .kind(TypeKind.CLASS)
                .visibility(Visibility.PUBLIC)
                .modifiers("public")
                .supertypes(List.of(supertypes))
                .docSource(DocSource.SOURCES)
                .entryPath(name.sourceEntryPath())
                .build();
    }

    static MemberDescriptor method(String declaringType, String name, ParameterInfo... parameters) {
        return MemberDescriptor.builder()
                .declaringType(TypeName.of(declaringType))
                .name(name)
                .kind(MemberKind.METHOD)
                .signature("void " + name + "()")
                .parameters(List.of(parameters))
                .visibility(Visibility.PUBLIC)
                .build();
    }

    static MemberDescriptor field(String declaringType, String name) {
        return MemberDescriptor.builder()
                .declaringType(TypeName.of(declaringType))
                .name(name)
                .kind(MemberKind.FIELD)
                .signature("int " + name)
                .visibility(Visibility.PUBLIC)
                .range(Optional.empty())
                .build();
    }
}
