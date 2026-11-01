package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.index.MemberRepository;
import io.github.vecluu.jdoc.javadoc.JavadocTypePage;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.ParsedMember;
import io.github.vecluu.jdoc.source.ParsedType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * Loads full documentation on demand from the single container entry of a type.
 *
 * <ul>
 *   <li>{@link DocSource#SOURCES} / {@link DocSource#JDK}: the type's {@code .java} entry from the sources jar or
 *       {@code src.zip} (the JDK artifact's jar path), parsed with T04; a member is found by its stored offsets,
 *       else by kind, name and erased parameters.</li>
 *   <li>{@link DocSource#JAVADOC}: the type's page from the javadoc jar; the member section by anchor.</li>
 *   <li>{@link DocSource#DECOMPILED}: no docs.</li>
 * </ul>
 * Instance methods with a missing doc, an empty description or {@code {@inheritDoc}} are completed from the nearest
 * overridden supertype method that has a doc ({@link TypeHierarchy} order, see {@link DocInheritance}).
 * Parsed files are cached by {@link SourceFiles} / {@link JavadocDocs}.
 */
@RequiredArgsConstructor
public final class DocLoader {

    private final ArtifactFiles artifacts;
    private final SourceFiles sources;
    private final JavadocDocs javadoc;
    private final TypeHierarchy hierarchy;
    private final MemberRepository members;
    private final OverrideMatcher overrides;

    /** The type's own doc comment; {@link RenderedDoc#missing()} if there is none. */
    public RenderedDoc typeDoc(LocatedType type) {
        DocSource docSource = type.descriptor().docSource();
        return switch (docSource) {
            case SOURCES, JDK -> parsedType(type).map(ParsedType::doc).orElse(RenderedDoc.missing());
            case JAVADOC -> typePage(type).map(JavadocTypePage::typeDoc).orElse(RenderedDoc.missing());
            case DECOMPILED -> RenderedDoc.missing();
        };
    }

    /** The member's doc, completed from overridden supertype members where needed. */
    public MemberDoc memberDoc(MemberMatch member, LookupScope scope) {
        LocatedType declaring = member.declaringType();
        MemberDescriptor descriptor = member.descriptor();
        RenderedDoc doc = ownMemberDoc(declaring, descriptor);
        if (declaring.descriptor().docSource() == DocSource.DECOMPILED || !inheritsDocs(descriptor)
                || !DocInheritance.needsInheritance(doc)) {
            return new MemberDoc(doc, Optional.empty());
        }
        Optional<TypeName> inheritedFrom = Optional.empty();
        for (Supertype supertype : hierarchy.supertypes(declaring, scope)) {
            if (supertype.type().isEmpty()
                    || supertype.type().get().descriptor().docSource() == DocSource.DECOMPILED) {
                continue;
            }
            LocatedType candidate = supertype.type().get();
            Optional<IndexedMember> overridden = members.membersOf(candidate.id()).stream()
                    .filter(other -> other.descriptor().visibility().isApi() && inheritsDocs(other.descriptor()))
                    .filter(other -> overrides.overrides(descriptor, other.descriptor()))
                    .findFirst();
            if (overridden.isEmpty()) {
                continue;
            }
            RenderedDoc inherited = ownMemberDoc(candidate, overridden.get().descriptor());
            if (inherited.isEmpty()) {
                continue;
            }
            doc = DocInheritance.merge(doc, inherited);
            if (inheritedFrom.isEmpty()) {
                inheritedFrom = Optional.of(candidate.name());
            }
            if (!DocInheritance.needsInheritance(doc)) {
                break;
            }
        }
        return new MemberDoc(doc, inheritedFrom);
    }

    /** The member's own doc comment as found in its doc source, without inheritance. */
    public RenderedDoc ownMemberDoc(LocatedType declaring, MemberDescriptor member) {
        return switch (declaring.descriptor().docSource()) {
            case SOURCES, JDK -> parsedType(declaring)
                    .flatMap(type -> parsedMember(type, member))
                    .map(ParsedMember::doc)
                    .orElse(RenderedDoc.missing());
            case JAVADOC -> typePage(declaring).flatMap(page -> page.memberDoc(member)).orElse(RenderedDoc.missing());
            case DECOMPILED -> RenderedDoc.missing();
        };
    }

    /**
     * The doc of a package: {@code package-info.java} from a sources jar or {@code src.zip}, else
     * {@code package-summary.html} from a javadoc jar, trying the artifacts of the given types in their order.
     *
     * @param packageName    the package
     * @param typesInPackage types of the package in scope (they tell which artifacts and containers to look in)
     */
    public Optional<PackageDoc> packageDoc(String packageName, List<LocatedType> typesInPackage) {
        Map<Long, List<LocatedType>> byArtifact = new LinkedHashMap<>();
        for (LocatedType type : typesInPackage) {
            byArtifact.computeIfAbsent(type.artifact().id(), id -> new ArrayList<>()).add(type);
        }
        for (List<LocatedType> group : byArtifact.values()) {
            Optional<PackageDoc> doc = packageDocOf(packageName, group);
            if (doc.isPresent()) {
                return doc;
            }
        }
        return Optional.empty();
    }

    private Optional<PackageDoc> packageDocOf(String packageName, List<LocatedType> group) {
        ScopedArtifact scoped = group.getFirst().artifact();
        Optional<IndexedArtifact> artifact = artifacts.artifact(scoped.id());
        if (artifact.isEmpty()) {
            return Optional.empty();
        }
        String packagePath = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
        for (LocatedType type : group) {
            DocSource docSource = type.descriptor().docSource();
            Optional<Path> container = sourceContainer(artifact.get(), docSource);
            if (container.isEmpty()) {
                continue;
            }
            String entry = entryPrefix(type) + packagePath + "package-info.java";
            Optional<RenderedDoc> doc = sources.parse(container.get(), entry, docSource)
                    .flatMap(parsed -> parsed.file().packageDoc())
                    .filter(rendered -> !rendered.isEmpty());
            if (doc.isPresent()) {
                return doc.map(rendered -> new PackageDoc(rendered, scoped));
            }
            break;
        }
        return artifact.get().javadocJar()
                .flatMap(jar -> javadoc.packageDoc(jar, packageName))
                .map(rendered -> new PackageDoc(rendered, scoped));
    }

    /** Directory prefix of a source entry before its package path: {@code java.base/} in {@code src.zip}. */
    private static String entryPrefix(LocatedType type) {
        String entryPath = type.descriptor().entryPath();
        String relative = type.name().sourceEntryPath();
        return entryPath.endsWith(relative) ? entryPath.substring(0, entryPath.length() - relative.length()) : "";
    }

    /** The parsed source of a SOURCES/JDK type. */
    Optional<ParsedType> parsedType(LocatedType type) {
        DocSource docSource = type.descriptor().docSource();
        return artifacts.artifact(type.artifact().id())
                .flatMap(artifact -> sourceContainer(artifact, docSource))
                .flatMap(container -> sources.parse(container, type.descriptor().entryPath(), docSource))
                .flatMap(parsed -> parsed.file().type(type.name()));
    }

    /** The jar holding source text for a doc source: the sources jar, or {@code src.zip} for the JDK. */
    static Optional<Path> sourceContainer(IndexedArtifact artifact, DocSource docSource) {
        return switch (docSource) {
            case SOURCES -> artifact.sourcesJar();
            case JDK -> Optional.of(artifact.jarPath());
            case JAVADOC, DECOMPILED -> Optional.empty();
        };
    }

    /** The parsed member matching a stored descriptor: same start offset, else same kind and erased key. */
    static Optional<ParsedMember> parsedMember(ParsedType type, MemberDescriptor member) {
        if (member.range().isPresent()) {
            int start = member.range().get().start();
            Optional<ParsedMember> byOffset = type.members().stream()
                    .filter(parsed -> parsed.descriptor().range().map(range -> range.start() == start).orElse(false))
                    .filter(parsed -> parsed.descriptor().name().equals(member.name()))
                    .findFirst();
            if (byOffset.isPresent()) {
                return byOffset;
            }
        }
        return type.members().stream()
                .filter(parsed -> parsed.descriptor().kind() == member.kind())
                .filter(parsed -> parsed.descriptor().erasedKey().equals(member.erasedKey()))
                .findFirst();
    }

    private Optional<JavadocTypePage> typePage(LocatedType type) {
        return artifacts.artifact(type.artifact().id())
                .flatMap(IndexedArtifact::javadocJar)
                .flatMap(jar -> javadoc.typePage(jar, type.name()));
    }

    /** Only instance methods override, so only they inherit docs. */
    private static boolean inheritsDocs(MemberDescriptor member) {
        return member.kind() == MemberKind.METHOD && !member.isStatic();
    }
}
