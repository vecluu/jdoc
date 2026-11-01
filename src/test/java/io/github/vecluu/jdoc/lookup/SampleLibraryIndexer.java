package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.bytecode.ZipBinaryJarScanner;
import io.github.vecluu.jdoc.javadoc.JavadocJar;
import io.github.vecluu.jdoc.javadoc.JavadocJarReader;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.javadoc.JavadocTypePage;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSource;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.KnownTypes;
import io.github.vecluu.jdoc.source.ParsedMember;
import io.github.vecluu.jdoc.source.ParsedSourceFile;
import io.github.vecluu.jdoc.source.ParsedType;
import io.github.vecluu.jdoc.testsupport.BuiltLibrary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IT helper standing in for T08's indexer: indexes a built library with the per-type fallback (sources jar, then
 * javadoc page, then decompiled), calling T04/T05 directly. Supertypes always come from bytecode.
 */
final class SampleLibraryIndexer {

    private SampleLibraryIndexer() {
    }

    static ScopedArtifact index(LookupIndex index, BuiltLibrary library) {
        List<ScannedClass> scanned = new ZipBinaryJarScanner().scanAll(library.binaryJar());
        KnownTypes known = KnownTypes.of(scanned.stream().map(scannedClass -> scannedClass.type().name()).toList());
        Map<TypeName, List<ScannedClass>> byTopLevel = new LinkedHashMap<>();
        scanned.forEach(scannedClass -> byTopLevel
                .computeIfAbsent(scannedClass.type().name().topLevel(), name -> new ArrayList<>()).add(scannedClass));
        List<TypeDescriptor> types = new ArrayList<>();
        List<MemberDescriptor> members = new ArrayList<>();
        ZipJarEntryReader reader = new ZipJarEntryReader();
        try (JavaSourceParser parser = new JavaSourceParser()) {
            for (Map.Entry<TypeName, List<ScannedClass>> group : byTopLevel.entrySet()) {
                String sourcePath = group.getKey().sourceEntryPath();
                Optional<String> text = library.sourcesJar().flatMap(jar -> reader.readText(jar, sourcePath));
                if (text.isPresent()) {
                    ParsedSourceFile file = parser.parse(new JavaSource(sourcePath, text.get(), DocSource.SOURCES),
                            known);
                    for (ScannedClass scannedClass : group.getValue()) {
                        ParsedType parsed = file.type(scannedClass.type().name()).orElseThrow();
                        types.add(parsed.descriptor().toBuilder().supertypes(scannedClass.type().supertypes())
                                .build());
                        parsed.members().stream().map(ParsedMember::descriptor)
                                .filter(member -> member.visibility().isApi())
                                .forEach(members::add);
                    }
                } else {
                    for (ScannedClass scannedClass : group.getValue()) {
                        Optional<JavadocTypePage> page = page(library, scannedClass.type().name());
                        if (page.isPresent()) {
                            types.add(scannedClass.type().toBuilder()
                                    .docSource(DocSource.JAVADOC)
                                    .entryPath(scannedClass.type().name().javadocPagePath())
                                    .firstSentence(page.get().typeDoc().firstSentence())
                                    .build());
                            scannedClass.members().forEach(member -> members.add(member.toBuilder()
                                    .firstSentence(page.get().memberDoc(member).map(RenderedDoc::firstSentence)
                                            .orElse(""))
                                    .build()));
                        } else {
                            types.add(scannedClass.type());
                            members.addAll(scannedClass.members());
                        }
                    }
                }
            }
        }
        IndexedArtifact artifact = LookupIndex.artifact(ArtifactKey.maven(library.gav())).toBuilder()
                .jarPath(library.binaryJar())
                .sourcesJar(library.sourcesJar())
                .javadocJar(library.javadocJar())
                .build();
        return index.add(artifact, types, members);
    }

    private static Optional<JavadocTypePage> page(BuiltLibrary library, TypeName type) {
        if (library.javadocJar().isEmpty()) {
            return Optional.empty();
        }
        try (JavadocJar jar = JavadocJar.open(library.javadocJar().get())) {
            return new JavadocJarReader(jar, new JavadocPageParser()).typePage(type);
        }
    }
}
