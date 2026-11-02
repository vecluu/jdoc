package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.decompile.DecompiledSourceProvider;
import io.github.vecluu.jdoc.index.IndexDatabase;
import io.github.vecluu.jdoc.index.JdbcArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcDocRepository;
import io.github.vecluu.jdoc.index.JdbcFailedArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcMemberRepository;
import io.github.vecluu.jdoc.index.JdbcSupertypeRepository;
import io.github.vecluu.jdoc.index.JdbcTypeRepository;
import io.github.vecluu.jdoc.indexer.MemberEntry;
import io.github.vecluu.jdoc.indexer.SqliteIndexStore;
import io.github.vecluu.jdoc.indexer.StoredArtifact;
import io.github.vecluu.jdoc.indexer.TypeEntry;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import io.github.vecluu.jdoc.source.JavaSource;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.KnownTypes;
import io.github.vecluu.jdoc.source.ParsedSourceFile;
import io.github.vecluu.jdoc.source.ParsedType;
import io.github.vecluu.jdoc.testsupport.JarWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Test helper: a real SQLite index in a temp directory, populated through the indexer's {@link SqliteIndexStore} (so
 * docs are stored exactly as the indexer stores them) with hand-built descriptors or with types and docs parsed by
 * {@link JavaSourceParser} from hand-written sources (also written to a real sources jar or {@code src.zip} for
 * {@code jdoc source}).
 */
final class LookupIndex implements AutoCloseable {

    final IndexDatabase database;
    final JdbcArtifactRepository artifacts;
    final JdbcTypeRepository types;
    final JdbcSupertypeRepository supertypes;
    final JdbcMemberRepository members;
    final JdbcDocRepository docs;
    private final SqliteIndexStore store;
    private final Path directory;
    private final List<ScopedArtifact> scope = new ArrayList<>();
    private JavaSourceParser parser;

    private LookupIndex(Path directory) {
        this.directory = directory;
        this.database = IndexDatabase.open(directory.resolve("index.db"), Duration.ofSeconds(5));
        this.artifacts = new JdbcArtifactRepository(database);
        this.types = new JdbcTypeRepository(database);
        this.supertypes = new JdbcSupertypeRepository(database);
        this.members = new JdbcMemberRepository(database);
        this.docs = new JdbcDocRepository(database);
        this.store = new SqliteIndexStore(database, artifacts, types, members,
                new JdbcFailedArtifactRepository(database), docs);
    }

    static LookupIndex open(Path directory) {
        return new LookupIndex(directory);
    }

    /** All artifacts added so far, in the order added (classpath order). */
    LookupScope scope() {
        return new LookupScope(scope, ResolutionStatus.RESOLVED);
    }

    LookupServices services(DecompiledSourceProvider decompiled) {
        return services(decompiled, new ZipJarEntryReader());
    }

    /** Services reading source entries through {@code jarEntries} (e.g. to simulate a changed sources jar). */
    LookupServices services(DecompiledSourceProvider decompiled, JarEntryReader jarEntries) {
        return LookupServices.create(artifacts, types, supertypes, members, docs, jarEntries, decompiled);
    }

    LookupServices services() {
        return services((gav, jar, type) -> {
            throw new AssertionError("unexpected decompile of " + type);
        });
    }

    /** The store, for indexing with the real indexers; call {@link #register} with the result. */
    SqliteIndexStore store() {
        return store;
    }

    /** Adds an artifact stored by someone else (e.g. a real indexer) to the scope. */
    ScopedArtifact register(StoredArtifact stored) {
        ScopedArtifact scoped = new ScopedArtifact(stored.artifact().id(), stored.artifact().key());
        scope.add(scoped);
        return scoped;
    }

    /** Adds an artifact with hand-built types and members without docs (members attach to their declaring type). */
    ScopedArtifact add(IndexedArtifact artifact, List<TypeDescriptor> typeDescriptors,
            List<MemberDescriptor> memberDescriptors) {
        Map<TypeName, List<MemberDescriptor>> byType = new LinkedHashMap<>();
        typeDescriptors.forEach(type -> byType.put(type.name(), new ArrayList<>()));
        for (MemberDescriptor member : memberDescriptors) {
            List<MemberDescriptor> owner = byType.get(member.declaringType());
            if (owner == null) {
                throw new IllegalArgumentException("no type for " + member.reference());
            }
            owner.add(member);
        }
        return add(artifact, typeDescriptors.stream()
                .map(type -> TypeEntry.undocumented(type, byType.get(type.name())))
                .toList(), Map.of());
    }

    /** Adds an artifact with types, members and docs, plus package docs, as the indexer stores them. */
    ScopedArtifact add(IndexedArtifact artifact, List<TypeEntry> entries, Map<String, RenderedDoc> packageDocs) {
        return register(store.replace(artifact, sink -> {
            sink.types(entries);
            packageDocs.forEach(sink::packageDoc);
        }));
    }

    ScopedArtifact add(String gav, List<TypeDescriptor> typeDescriptors, MemberDescriptor... memberDescriptors) {
        return add(artifact(ArtifactKey.maven(Gav.parse(gav))), typeDescriptors, Arrays.asList(memberDescriptors));
    }

    /**
     * Writes the sources into a jar ({@code <name>.jar}), parses them with {@link JavaSourceParser} and indexes every
     * type with its API members. For the JDK, entries get the module prefix (e.g. {@code java.base/}).
     *
     * @param key        artifact key
     * @param docSource  {@link DocSource#SOURCES} (sources jar) or {@link DocSource#JDK} (src.zip as the jar path)
     * @param prefix     entry prefix, e.g. {@code java.base/}, or empty
     * @param sources    path (without prefix) to text
     */
    ScopedArtifact addSources(ArtifactKey key, DocSource docSource, String prefix, Map<String, String> sources) {
        Path jar = writeJar(key.keyString().replace(':', '_') + (docSource == DocSource.JDK ? "-src.zip"
                : "-sources.jar"), prefix, sources);
        List<ParsedSourceFile> files = parseAll(prefix, sources, docSource);
        List<TypeEntry> entries = new ArrayList<>();
        Map<String, RenderedDoc> packageDocs = new LinkedHashMap<>();
        for (ParsedSourceFile file : files) {
            file.packageDoc().filter(doc -> !doc.isEmpty()).ifPresent(doc -> packageDocs.put(file.packageName(), doc));
            for (ParsedType type : file.types()) {
                entries.add(entry(type));
            }
        }
        IndexedArtifact.IndexedArtifactBuilder artifact = artifact(key).toBuilder();
        if (docSource == DocSource.JDK) {
            artifact.jarPath(jar);
        } else {
            artifact.sourcesJar(Optional.of(jar));
        }
        return add(artifact.build(), entries, packageDocs);
    }

    /** A parsed type as the indexer stores it: its doc and its API members with their docs. */
    static TypeEntry entry(ParsedType type) {
        return new TypeEntry(type.descriptor(), type.doc(), type.members().stream()
                .filter(member -> member.descriptor().visibility().isApi())
                .map(member -> new MemberEntry(member.descriptor(), member.doc()))
                .toList());
    }

    /** {@link #addSources} for a Maven artifact's sources jar. */
    ScopedArtifact addSources(String gav, Map<String, String> sources) {
        return addSources(ArtifactKey.maven(Gav.parse(gav)), DocSource.SOURCES, "", sources);
    }

    private List<ParsedSourceFile> parseAll(String prefix, Map<String, String> sources, DocSource docSource) {
        List<TypeName> known = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            parser().parse(new JavaSource(prefix + source.getKey(), source.getValue(), docSource)).types()
                    .forEach(type -> known.add(type.name()));
        }
        List<ParsedSourceFile> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            files.add(parser().parse(new JavaSource(prefix + source.getKey(), source.getValue(), docSource),
                    KnownTypes.of(known)));
        }
        return files;
    }

    Path writeJar(String fileName, String prefix, Map<String, String> entries) {
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        entries.forEach((path, text) -> bytes.put(prefix + path, text.getBytes(StandardCharsets.UTF_8)));
        try {
            Files.createDirectories(directory.resolve("jars"));
            return JarWriter.writeEntries(bytes, directory.resolve("jars").resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JavaSourceParser parser() {
        if (parser == null) {
            parser = new JavaSourceParser();
        }
        return parser;
    }

    static IndexedArtifact artifact(ArtifactKey key) {
        return IndexedArtifact.builder()
                .key(key)
                .jarPath(Path.of("/repo/" + key.keyString().replace(':', '/') + ".jar"))
                .jarMtime(1L)
                .jarSize(1L)
                .indexedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    static TypeDescriptor type(String binaryName, TypeKind kind, Visibility visibility, String... supertypes) {
        TypeName name = TypeName.of(binaryName);
        return TypeDescriptor.builder()
                .name(name)
                .kind(kind)
                .visibility(visibility)
                .modifiers(visibility.keyword())
                .supertypes(Arrays.stream(supertypes).map(TypeName::of).toList())
                .firstSentence("About " + name.simpleName() + ".")
                .docSource(DocSource.DECOMPILED)
                .entryPath(name.classEntryPath())
                .build();
    }

    static TypeDescriptor publicClass(String binaryName, String... supertypes) {
        return type(binaryName, TypeKind.CLASS, Visibility.PUBLIC, supertypes);
    }

    static TypeDescriptor publicInterface(String binaryName, String... supertypes) {
        return type(binaryName, TypeKind.INTERFACE, Visibility.PUBLIC, supertypes);
    }

    /** A public instance method; parameters as {@code "Type name"} or {@code "Type name:Erased"}. */
    static MemberDescriptor method(String declaringType, String name, String... parameters) {
        return member(declaringType, name, MemberKind.METHOD, false, parameters);
    }

    static MemberDescriptor staticMethod(String declaringType, String name, String... parameters) {
        return member(declaringType, name, MemberKind.METHOD, true, parameters);
    }

    static MemberDescriptor constructor(String declaringType, String... parameters) {
        return member(declaringType, TypeName.of(declaringType).simpleName(), MemberKind.CONSTRUCTOR, false,
                parameters);
    }

    static MemberDescriptor field(String declaringType, String name) {
        return member(declaringType, name, MemberKind.FIELD, false);
    }

    static MemberDescriptor member(String declaringType, String name, MemberKind kind, boolean isStatic,
            String... parameters) {
        List<ParameterInfo> infos = Arrays.stream(parameters).map(LookupIndex::parameter).toList();
        String parameterText = String.join(", ", infos.stream().map(ParameterInfo::declaration).toList());
        String signature = switch (kind) {
            case METHOD -> (isStatic ? "static " : "") + "void " + name + "(" + parameterText + ")";
            case CONSTRUCTOR -> name + "(" + parameterText + ")";
            case FIELD -> "int " + name;
            case ENUM_CONSTANT -> name;
        };
        return MemberDescriptor.builder()
                .declaringType(TypeName.of(declaringType))
                .name(name)
                .kind(kind)
                .signature(signature)
                .parameters(infos)
                .visibility(Visibility.PUBLIC)
                .isStatic(isStatic)
                .firstSentence("Does " + name + ".")
                .build();
    }

    private static ParameterInfo parameter(String text) {
        String[] erasedSplit = text.split(":");
        String[] parts = erasedSplit[0].trim().split(" ");
        String type = parts[0];
        String erased = erasedSplit.length > 1 ? erasedSplit[1] : type.replaceAll("<.*>", "").replace("...", "[]");
        return new ParameterInfo(parts.length > 1 ? parts[1] : "arg", type, erased);
    }

    @Override
    public void close() {
        if (parser != null) {
            parser.close();
        }
        database.close();
    }
}
