package io.github.vecluu.jdoc.app;

import io.github.vecluu.jdoc.decompile.DecompiledSourceProvider;
import io.github.vecluu.jdoc.index.IndexDatabase;
import io.github.vecluu.jdoc.index.JdbcArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcMemberRepository;
import io.github.vecluu.jdoc.index.JdbcSupertypeRepository;
import io.github.vecluu.jdoc.index.JdbcTypeRepository;
import io.github.vecluu.jdoc.indexer.IndexReport;
import io.github.vecluu.jdoc.lookup.JarJavadocDocs;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.lookup.LookupServices;
import io.github.vecluu.jdoc.lookup.ScopedArtifact;
import io.github.vecluu.jdoc.lookup.ZipJarEntryReader;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSource;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.KnownTypes;
import io.github.vecluu.jdoc.source.ParsedMember;
import io.github.vecluu.jdoc.source.ParsedSourceFile;
import io.github.vecluu.jdoc.source.ParsedType;
import io.github.vecluu.jdoc.testsupport.JarWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test helper: a real SQLite index in a temp directory filled with types parsed (T04) from hand-written sources, and
 * a {@link ModuleContext} fake that hands out sessions on it, so app services are tested without Maven or indexing.
 */
final class AppIndex implements AutoCloseable {

    private final Path directory;
    private final IndexDatabase database;
    private final JdbcArtifactRepository artifacts;
    private final JdbcTypeRepository types;
    private final JdbcSupertypeRepository supertypes;
    private final JdbcMemberRepository members;
    private final List<ScopedArtifact> scope = new ArrayList<>();

    private AppIndex(Path directory) {
        this.directory = directory;
        this.database = IndexDatabase.open(directory.resolve("index.db"), Duration.ofSeconds(5));
        this.artifacts = new JdbcArtifactRepository(database);
        this.types = new JdbcTypeRepository(database);
        this.supertypes = new JdbcSupertypeRepository(database);
        this.members = new JdbcMemberRepository(database);
    }

    /** An index with {@link AppSources#jdk()} and {@link AppSources#library()} (classpath order: JDK first). */
    static AppIndex standard(Path directory) {
        AppIndex index = new AppIndex(directory);
        index.addJdk(AppSources.jdk());
        index.addLibrary(AppSources.LIBRARY_GAV, AppSources.library());
        return index;
    }

    ScopedArtifact addJdk(Map<String, String> sources) {
        return add(ArtifactKey.jdk(25), DocSource.JDK, "java.base/", sources);
    }

    ScopedArtifact addLibrary(String gav, Map<String, String> sources) {
        return add(ArtifactKey.maven(Gav.parse(gav)), DocSource.SOURCES, "", sources);
    }

    /** A context answering with this index; resolution status and warnings as given. */
    FakeModuleContext context(ResolutionStatus status, List<String> warnings) {
        return new FakeModuleContext(this, status, warnings);
    }

    /** A resolved context without warnings. */
    FakeModuleContext context() {
        return context(ResolutionStatus.RESOLVED, List.of());
    }

    ModuleSession session(ResolutionStatus status, List<String> warnings, IndexReport report) {
        ModuleClasspath classpath = new ModuleClasspath(directory.resolve("module"), "key", List.of(), status,
                warnings);
        return new ModuleSession(classpath, new LookupScope(scope, status), services(), report);
    }

    private LookupServices services() {
        DecompiledSourceProvider noDecompiler = (gav, jar, type) -> {
            throw new AssertionError("unexpected decompile of " + type);
        };
        return LookupServices.create(artifacts, types, supertypes, members, new ZipJarEntryReader(),
                new JarJavadocDocs(), noDecompiler);
    }

    private ScopedArtifact add(ArtifactKey key, DocSource docSource, String prefix, Map<String, String> sources) {
        Path jar = writeJar(key.keyString().replace(':', '_') + (docSource == DocSource.JDK ? "-src.zip"
                : "-sources.jar"), prefix, sources);
        List<TypeDescriptor> typeDescriptors = new ArrayList<>();
        List<MemberDescriptor> memberDescriptors = new ArrayList<>();
        for (ParsedSourceFile file : parse(prefix, sources, docSource)) {
            for (ParsedType type : file.types()) {
                typeDescriptors.add(type.descriptor());
                type.members().stream().map(ParsedMember::descriptor)
                        .filter(member -> member.visibility().isApi())
                        .forEach(memberDescriptors::add);
            }
        }
        IndexedArtifact.IndexedArtifactBuilder artifact = IndexedArtifact.builder()
                .key(key)
                .jarPath(docSource == DocSource.JDK ? jar : Paths.get("/repo/" + key.keyString() + ".jar"))
                .jarMtime(1L)
                .jarSize(1L)
                .indexedAt(Instant.parse("2026-01-01T00:00:00Z"));
        if (docSource == DocSource.SOURCES) {
            artifact.sourcesJar(Optional.of(jar));
        }
        IndexedArtifact saved = artifacts.save(artifact.build());
        List<IndexedType> stored = types.insertAll(saved.id(), typeDescriptors);
        Map<Long, List<MemberDescriptor>> byType = new LinkedHashMap<>();
        for (MemberDescriptor member : memberDescriptors) {
            IndexedType owner = stored.stream().filter(type -> type.name().equals(member.declaringType()))
                    .findFirst().orElseThrow();
            byType.computeIfAbsent(owner.id(), id -> new ArrayList<>()).add(member);
        }
        members.insertAll(byType);
        ScopedArtifact scoped = new ScopedArtifact(saved.id(), saved.key());
        scope.add(scoped);
        return scoped;
    }

    private static List<ParsedSourceFile> parse(String prefix, Map<String, String> sources, DocSource docSource) {
        try (JavaSourceParser parser = new JavaSourceParser()) {
            List<TypeName> known = new ArrayList<>();
            sources.forEach((path, text) -> parser.parse(new JavaSource(prefix + path, text, docSource)).types()
                    .forEach(type -> known.add(type.name())));
            List<ParsedSourceFile> files = new ArrayList<>();
            sources.forEach((path, text) -> files.add(
                    parser.parse(new JavaSource(prefix + path, text, docSource), KnownTypes.of(known))));
            return files;
        }
    }

    private Path writeJar(String fileName, String prefix, Map<String, String> entries) {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        entries.forEach((path, text) -> bytes.put(prefix + path, text.getBytes(StandardCharsets.UTF_8)));
        try {
            Files.createDirectories(directory.resolve("jars"));
            return JarWriter.writeEntries(bytes, directory.resolve("jars").resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        database.close();
    }
}
