package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.javadoc.JavadocJarReader;
import io.github.vecluu.jdoc.javadoc.JavadocTypePage;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.ParsedSourceFile;
import io.github.vecluu.jdoc.source.ParsedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The per-type doc-source fallback for the types of one top-level class of a binary jar:
 * <ol>
 *   <li>the sources jar has the top-level type's {@code .java} file and the parsed file declares the type →
 *       {@link DocSource#SOURCES}: descriptors and members from source, but direct supertypes from bytecode (exact,
 *       never guessed);</li>
 *   <li>else the javadoc jar has the type's page → {@link DocSource#JAVADOC}: bytecode signatures plus the docs
 *       rendered from the page; parameter names come from the page's member signature when its section matches
 *       the member exactly (name and erased parameter types), else the bytecode names stay;</li>
 *   <li>else {@link DocSource#DECOMPILED}: bytecode signatures, no docs.</li>
 * </ol>
 * The source file is looked up once per top-level type, so nested types follow it; a type missing from the parsed
 * file (generated) falls through on its own. Only {@code .java} files count. Members are API members only.
 * Not thread-safe.
 */
final class TypeDocResolver {

    private final Optional<SourceFiles> sources;
    private final Optional<JavadocJarReader> javadoc;

    /**
     * @param sources the files of the opened sources jar, if any
     * @param javadoc a reader on the opened javadoc jar, if any
     */
    TypeDocResolver(Optional<SourceFiles> sources, Optional<JavadocJarReader> javadoc) {
        this.sources = sources;
        this.javadoc = javadoc;
    }

    /**
     * Resolves the types of one top-level class.
     *
     * @param topLevel the top-level type
     * @param classes  the top-level type and its nested types as scanned from the binary jar
     */
    List<TypeEntry> resolve(TypeName topLevel, List<ScannedClass> classes) {
        Optional<ParsedSourceFile> parsed = sources.flatMap(files -> files.parse(topLevel.sourceEntryPath()));
        List<TypeEntry> result = new ArrayList<>(classes.size());
        for (ScannedClass scanned : classes) {
            Optional<ParsedType> source = parsed.flatMap(file -> file.type(scanned.type().name()));
            if (source.isPresent()) {
                result.add(fromSource(scanned, source.get()));
                continue;
            }
            Optional<JavadocTypePage> page = javadoc.flatMap(reader -> reader.typePage(scanned.type().name()));
            result.add(page.map(p -> fromJavadoc(scanned, p)).orElseGet(() -> decompiled(scanned)));
        }
        return result;
    }

    /**
     * The doc of a package: a non-empty package comment of {@code package-info.java} in the sources jar, else the
     * javadoc jar's {@code package-summary.html}.
     */
    Optional<RenderedDoc> packageDoc(String packageName) {
        String path = (packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/") + "package-info.java";
        Optional<RenderedDoc> fromSource = sources.flatMap(files -> files.parse(path))
                .flatMap(ParsedSourceFile::packageDoc)
                .filter(doc -> !doc.isEmpty());
        if (fromSource.isPresent()) {
            return fromSource;
        }
        return javadoc.flatMap(reader -> reader.packageDoc(packageName)).filter(doc -> !doc.isEmpty());
    }

    private static TypeEntry fromSource(ScannedClass scanned, ParsedType source) {
        TypeDescriptor type = source.descriptor().toBuilder()
                .supertypes(scanned.type().supertypes())
                .docSource(DocSource.SOURCES)
                .build();
        return new TypeEntry(type, source.doc(), apiMembers(source));
    }

    /** The API members of a parsed type with their docs. */
    static List<MemberEntry> apiMembers(ParsedType source) {
        return source.members().stream()
                .filter(member -> member.descriptor().visibility().isApi())
                .map(member -> new MemberEntry(member.descriptor(), member.doc()))
                .toList();
    }

    private static TypeEntry fromJavadoc(ScannedClass scanned, JavadocTypePage page) {
        TypeDescriptor type = scanned.type().toBuilder()
                .docSource(DocSource.JAVADOC)
                .entryPath(scanned.type().name().javadocPagePath())
                .firstSentence(page.typeDoc().firstSentence())
                .build();
        List<MemberEntry> members = scanned.members().stream()
                .map(member -> {
                    RenderedDoc doc = page.memberDoc(member).orElse(RenderedDoc.missing());
                    MemberDescriptor named = page.parameterNames(member)
                            .map(names -> ParameterNames.apply(member, names))
                            .orElse(member);
                    return new MemberEntry(named.toBuilder().firstSentence(doc.firstSentence()).build(), doc);
                })
                .toList();
        return new TypeEntry(type, page.typeDoc(), members);
    }

    private static TypeEntry decompiled(ScannedClass scanned) {
        return TypeEntry.undocumented(scanned.type().toBuilder().docSource(DocSource.DECOMPILED).build(),
                scanned.members());
    }
}
