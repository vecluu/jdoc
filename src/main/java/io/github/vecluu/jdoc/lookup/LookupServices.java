package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.decompile.DecompiledSourceProvider;
import io.github.vecluu.jdoc.index.ArtifactRepository;
import io.github.vecluu.jdoc.index.MemberRepository;
import io.github.vecluu.jdoc.index.SupertypeRepository;
import io.github.vecluu.jdoc.index.TypeRepository;

/**
 * All lookup services of one call, wired together. Closing it releases the javac parser if one was created.
 *
 * @param types        scoped type access and API visibility
 * @param typeResolver {@code <type>} arguments
 * @param hierarchy    supertypes and subtypes ({@code impls})
 * @param typeMembers  declared and inherited members
 * @param classView    {@code jdoc class}
 * @param memberLookup {@code jdoc method} / {@code jdoc source} targets
 * @param docs         on-demand docs
 * @param sources      on-demand member source ({@code jdoc source})
 * @param find         {@code jdoc find}
 * @param memberSearch {@code jdoc member}
 * @param packages     {@code jdoc package}
 * @param sourceFiles  the shared parsed-file cache (closed by {@link #close()})
 */
public record LookupServices(
        ScopedTypes types,
        TypeResolver typeResolver,
        TypeHierarchy hierarchy,
        TypeMembers typeMembers,
        ClassViewService classView,
        MemberLookupService memberLookup,
        DocLoader docs,
        SourceLoader sources,
        FindService find,
        MemberSearchService memberSearch,
        PackageService packages,
        SourceFiles sourceFiles) implements AutoCloseable {

    /**
     * Wires every service from the index repositories and the jar boundaries.
     *
     * @param jarEntries  reads source entries (normally {@link ZipJarEntryReader})
     * @param javadoc     reads javadoc pages (normally {@link JarJavadocDocs})
     * @param decompiled  decompiled-source cache for {@code jdoc source} of types without sources
     */
    public static LookupServices create(
            ArtifactRepository artifactRepository,
            TypeRepository typeRepository,
            SupertypeRepository supertypeRepository,
            MemberRepository memberRepository,
            JarEntryReader jarEntries,
            JavadocDocs javadoc,
            DecompiledSourceProvider decompiled) {
        ScopedTypes types = new ScopedTypes(typeRepository);
        TypeResolver resolver = new TypeResolver(types);
        TypeHierarchy hierarchy = new TypeHierarchy(types, supertypeRepository);
        OverrideMatcher overrides = new OverrideMatcher();
        TypeMembers typeMembers = new TypeMembers(memberRepository, hierarchy, overrides);
        ArtifactFiles artifactFiles = new ArtifactFiles(artifactRepository);
        SourceFiles sourceFiles = new SourceFiles(jarEntries);
        DocLoader docs = new DocLoader(artifactFiles, sourceFiles, javadoc, hierarchy, memberRepository, overrides);
        return new LookupServices(
                types,
                resolver,
                hierarchy,
                typeMembers,
                new ClassViewService(types, hierarchy, typeMembers, docs),
                new MemberLookupService(resolver, typeMembers),
                docs,
                new SourceLoader(artifactFiles, sourceFiles, decompiled, docs),
                new FindService(types, memberRepository),
                new MemberSearchService(types, memberRepository),
                new PackageService(types, docs),
                sourceFiles);
    }

    @Override
    public void close() {
        sourceFiles.close();
    }
}
