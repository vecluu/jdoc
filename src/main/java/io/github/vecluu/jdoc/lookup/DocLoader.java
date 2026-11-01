package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.index.DocRepository;
import io.github.vecluu.jdoc.index.MemberRepository;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Reads documentation from the index: the indexer stores the rendered doc of every type, member and package
 * ({@link DocRepository}), so no source is parsed and no javadoc page is read here. Decompiled types have no docs.
 *
 * <p>Instance methods with a missing doc, an empty description or {@code {@inheritDoc}} are completed from the
 * nearest overridden supertype method that has a stored doc ({@link TypeHierarchy} order, see
 * {@link DocInheritance}). This happens here and not at index time because supertypes may live in other artifacts.
 */
@RequiredArgsConstructor
public final class DocLoader {

    private final DocRepository docs;
    private final TypeHierarchy hierarchy;
    private final MemberRepository members;
    private final OverrideMatcher overrides;

    /** The type's own doc comment; {@link RenderedDoc#missing()} if there is none. */
    public RenderedDoc typeDoc(LocatedType type) {
        if (type.descriptor().docSource() == DocSource.DECOMPILED) {
            return RenderedDoc.missing();
        }
        return docs.typeDoc(type.id()).orElse(RenderedDoc.missing());
    }

    /** The member's doc, completed from overridden supertype members where needed. */
    public MemberDoc memberDoc(MemberMatch member, LookupScope scope) {
        LocatedType declaring = member.declaringType();
        MemberDescriptor descriptor = member.descriptor();
        RenderedDoc doc = ownMemberDoc(declaring, member.member());
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
            RenderedDoc inherited = ownMemberDoc(candidate, overridden.get());
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

    /** The member's own doc comment as stored at index time, without inheritance. */
    public RenderedDoc ownMemberDoc(LocatedType declaring, IndexedMember member) {
        if (declaring.descriptor().docSource() == DocSource.DECOMPILED) {
            return RenderedDoc.missing();
        }
        return docs.memberDoc(member.id()).orElse(RenderedDoc.missing());
    }

    /**
     * The doc of a package, from the first artifact (in the order of the given types) that stored one: its
     * {@code package-info.java} from a sources jar or {@code src.zip}, else {@code package-summary.html} from a
     * javadoc jar.
     *
     * @param packageName    the package
     * @param typesInPackage types of the package in scope (they tell which artifacts to look in)
     */
    public Optional<PackageDoc> packageDoc(String packageName, List<LocatedType> typesInPackage) {
        Set<ScopedArtifact> artifacts = new LinkedHashSet<>();
        typesInPackage.forEach(type -> artifacts.add(type.artifact()));
        for (ScopedArtifact artifact : artifacts) {
            Optional<RenderedDoc> doc = docs.packageDoc(artifact.id(), packageName).filter(found -> !found.isEmpty());
            if (doc.isPresent()) {
                return Optional.of(new PackageDoc(doc.get(), artifact));
            }
        }
        return Optional.empty();
    }

    /** Only instance methods override, so only they inherit docs. */
    private static boolean inheritsDocs(MemberDescriptor member) {
        return member.kind() == MemberKind.METHOD && !member.isStatic();
    }
}
