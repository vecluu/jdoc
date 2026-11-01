package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.decompile.DecompilationException;
import io.github.vecluu.jdoc.decompile.DecompiledSourceProvider;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.MemberSourceSlicer;
import io.github.vecluu.jdoc.source.ParsedMember;
import io.github.vecluu.jdoc.source.ParsedType;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Source text of a member for {@code jdoc source}: the only place implementation is shown.
 *
 * <ul>
 *   <li>{@link DocSource#SOURCES} / {@link DocSource#JDK}: the member is cut from the source entry by its offsets
 *       (doc comment through end of body).</li>
 *   <li>{@link DocSource#JAVADOC} / {@link DocSource#DECOMPILED}: the top-level type is decompiled through the
 *       decompiled-source cache, parsed, and the member is cut from it (marked decompiled). For javadoc-jar types the
 *       rendered doc from the javadoc page is returned alongside.</li>
 * </ul>
 * Compiler-generated members (default constructors, record accessors, enum {@code values}) have no source.
 */
@Slf4j
@RequiredArgsConstructor
public final class SourceLoader {

    private final ArtifactFiles artifacts;
    private final SourceFiles sources;
    private final DecompiledSourceProvider decompiled;
    private final DocLoader docs;
    private final MemberSourceSlicer slicer;

    /** A loader with the standard slicer. */
    public SourceLoader(ArtifactFiles artifacts, SourceFiles sources, DecompiledSourceProvider decompiled,
            DocLoader docs) {
        this(artifacts, sources, decompiled, docs, new MemberSourceSlicer());
    }

    /** The member's source, or why there is none. */
    public MemberSource source(MemberMatch member, LookupScope scope) {
        LocatedType type = member.declaringType();
        Optional<IndexedArtifact> artifact = artifacts.artifact(type.artifact().id());
        if (artifact.isEmpty()) {
            return new MemberSource.Unavailable(member, "artifact of " + type.name().displayName()
                    + " is no longer indexed");
        }
        DocSource docSource = type.descriptor().docSource();
        return switch (docSource) {
            case SOURCES, JDK -> fromSourceText(member, artifact.get(), docSource);
            case JAVADOC, DECOMPILED -> fromDecompiled(member, artifact.get(), docSource, scope);
        };
    }

    private MemberSource fromSourceText(MemberMatch member, IndexedArtifact artifact, DocSource docSource) {
        LocatedType type = member.declaringType();
        Optional<Path> container = DocLoader.sourceContainer(artifact, docSource);
        Optional<ParsedText> parsed = container.flatMap(jar ->
                sources.parse(jar, type.descriptor().entryPath(), docSource));
        if (parsed.isEmpty()) {
            return new MemberSource.Unavailable(member, "source file " + type.descriptor().entryPath()
                    + " not found");
        }
        return slice(member, parsed.get(), Optional.empty(), false);
    }

    private MemberSource fromDecompiled(MemberMatch member, IndexedArtifact artifact, DocSource docSource,
            LookupScope scope) {
        TypeName name = member.declaringType().name();
        if (!(artifact.key() instanceof ArtifactKey.Maven maven)) {
            return new MemberSource.Unavailable(member, "no source for " + name.displayName()
                    + ": the JDK has no src.zip");
        }
        String text;
        try {
            text = decompiled.source(maven.gav(), artifact.jarPath(), name);
        } catch (DecompilationException e) {
            log.warn("{}", e.getMessage());
            log.debug("decompiler failure", e);
            return new MemberSource.Unavailable(member, e.getMessage());
        }
        ParsedText parsed = sources.parseText(name.sourceEntryPath(), text, DocSource.DECOMPILED);
        Optional<RenderedDoc> doc = docSource == DocSource.JAVADOC
                ? Optional.of(docs.memberDoc(member, scope).doc()).filter(rendered -> !rendered.isEmpty())
                : Optional.empty();
        return slice(member, parsed, doc, true);
    }

    private MemberSource slice(MemberMatch member, ParsedText parsed, Optional<RenderedDoc> doc,
            boolean decompiledText) {
        TypeName name = member.declaringType().name();
        Optional<ParsedType> type = parsed.file().type(name);
        Optional<ParsedMember> found = type.flatMap(parsedType -> decompiledText
                ? byShape(parsedType, member.descriptor())
                : DocLoader.parsedMember(parsedType, member.descriptor()));
        if (found.isEmpty()) {
            return new MemberSource.Unavailable(member, member.reference() + " not found in "
                    + (decompiledText ? "decompiled source" : "source file"));
        }
        if (found.get().implicit() || found.get().descriptor().range().isEmpty()) {
            return new MemberSource.Unavailable(member, member.reference()
                    + " is generated by the compiler and has no source");
        }
        return slicer.member(parsed.text(), found.get().descriptor())
                .<MemberSource>map(code -> new MemberSource.Available(member, code, doc, decompiledText))
                .orElseGet(() -> new MemberSource.Unavailable(member, "source range of " + member.reference()
                        + " is outside the file"));
    }

    /** Decompiled text has no stored offsets: match by kind and erased key, else by a unique name and arity. */
    private static Optional<ParsedMember> byShape(ParsedType type, MemberDescriptor member) {
        Optional<ParsedMember> exact = type.members().stream()
                .filter(parsed -> parsed.descriptor().kind() == member.kind())
                .filter(parsed -> parsed.descriptor().erasedKey().equals(member.erasedKey()))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        List<ParsedMember> loose = type.members().stream()
                .filter(parsed -> parsed.descriptor().kind() == member.kind())
                .filter(parsed -> parsed.descriptor().name().equals(member.name()))
                .filter(parsed -> parsed.descriptor().arity() == member.arity())
                .toList();
        return loose.size() == 1 ? Optional.of(loose.getFirst()) : Optional.empty();
    }
}
