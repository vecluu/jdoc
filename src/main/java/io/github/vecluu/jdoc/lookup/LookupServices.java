package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.decompile.DecompiledSourceProvider;
import io.github.vecluu.jdoc.index.ArtifactRepository;
import io.github.vecluu.jdoc.index.DocRepository;
import io.github.vecluu.jdoc.index.MemberRepository;
import io.github.vecluu.jdoc.index.SupertypeRepository;
import io.github.vecluu.jdoc.index.TypeRepository;

/**
 * All lookup services of one call, wired together. Closing it releases the javac parser if one was created. Only
 * {@link #sources()} ({@code jdoc source}) parses Java source or decompiles; every other service reads the index only.
 *
 * @param types        scoped type access and API visibility
 * @param typeResolver {@code <type>} arguments
 * @param hierarchy    supertypes and subtypes ({@code impls})
 * @param typeMembers  declared and inherited members
 * @param classView    {@code jdoc class}
 * @param memberLookup {@code jdoc method} / {@code jdoc source} targets
 * @param docs         docs stored in the index, with inheritance resolved
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
     * @param docRepository rendered docs stored at index time
     * @param jarEntries    reads source entries for {@code jdoc source} (normally {@link ZipJarEntryReader})
     * @param decompiled    decompiled-source cache for {@code jdoc source} of types without sources
     */
    public static LookupServices create(
            ArtifactRepository artifactRepository,
            TypeRepository typeRepository,
            SupertypeRepository supertypeRepository,
            MemberRepository memberRepository,
            DocRepository docRepository,
            JarEntryReader jarEntries,
            DecompiledSourceProvider decompiled) {
        ScopedTypes types = new ScopedTypes(typeRepository);
        TypeResolver resolver = new TypeResolver(types);
        TypeHierarchy hierarchy = new TypeHierarchy(types, supertypeRepository);
        OverrideMatcher overrides = new OverrideMatcher();
        TypeMembers typeMembers = new TypeMembers(memberRepository, hierarchy, overrides);
        ArtifactFiles artifactFiles = new ArtifactFiles(artifactRepository);
        SourceFiles sourceFiles = new SourceFiles(jarEntries);
        DocLoader docs = new DocLoader(docRepository, hierarchy, memberRepository, overrides);
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
