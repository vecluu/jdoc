package io.github.vecluu.jdoc.index;

import static io.github.vecluu.jdoc.index.IndexFixtures.artifact;
import static io.github.vecluu.jdoc.index.IndexFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.SourceRange;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcTypeRepositoryTest {

    private static final TypeName ENTRY = TypeName.of("java.util.Map$Entry");

    @TempDir
    Path tmp;

    private IndexDatabase database;
    private TypeRepository types;
    private long libA;
    private long libB;

    @BeforeEach
    void open() {
        database = IndexDatabase.open(tmp.resolve("index.db"), Duration.ofSeconds(5));
        types = new JdbcTypeRepository(database);
        ArtifactRepository artifacts = new JdbcArtifactRepository(database);
        libA = artifacts.save(artifact("g:a:1")).id();
        libB = artifacts.save(artifact("g:b:1")).id();
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void roundTripsEveryField() {
        TypeDescriptor full = TypeDescriptor.builder()
                .name(ENTRY)
                .kind(TypeKind.INTERFACE)
                .visibility(Visibility.PROTECTED)
                .modifiers("protected static")
                .typeParameters("<K, V>")
                .supertypes(List.of(TypeName.of("java.lang.Object"), TypeName.of("java.lang.Comparable")))
                .deprecated(true)
                .firstSentence("A map entry (key-value pair).")
                .docSource(DocSource.JAVADOC)
                .entryPath("java/util/Map.Entry.html")
                .declarationRange(Optional.of(new SourceRange(10, 200)))
                .build();
        TypeDescriptor minimal = TypeDescriptor.builder()
                .name(TypeName.of("Plain"))
                .kind(TypeKind.ANNOTATION)
                .visibility(Visibility.PACKAGE)
                .docSource(DocSource.DECOMPILED)
                .entryPath("Plain.class")
                .build();
        List<IndexedType> stored = types.insertAll(libA, List.of(full, minimal));

        assertThat(stored).extracting(IndexedType::descriptor).containsExactly(full, minimal);
        assertThat(stored).allSatisfy(t -> assertThat(t.artifactId()).isEqualTo(libA));
        assertThat(types.findById(stored.get(0).id())).contains(stored.get(0));
        assertThat(types.findById(stored.get(1).id())).contains(stored.get(1));
        assertThat(types.findById(-1)).isEmpty();
    }

    @Test
    void findsByBinarySimpleAndDisplayName() {
        IndexedType entry = types.insertAll(libA, List.of(type(ENTRY.binaryName()), type("java.util.Map"))).getFirst();
        List<Long> scope = List.of(libA);
        assertThat(types.findByBinaryName(ENTRY, scope)).containsExactly(entry);
        assertThat(types.findBySimpleName("entry", scope)).containsExactly(entry);
        assertThat(types.findBySimpleName("ENTRY", scope)).containsExactly(entry);
        assertThat(types.findByDisplayName("java.util.Map.Entry", scope)).containsExactly(entry);
        assertThat(types.findByDisplayName("java.util.map.entry", scope)).isEmpty();
        assertThat(types.findByBinaryName(TypeName.of("java.util.Map.Entry"), scope)).isEmpty();
    }

    @Test
    void scopesReadsByArtifactIds() {
        IndexedType inA = types.insertAll(libA, List.of(type("p.Dup"))).getFirst();
        IndexedType inB = types.insertAll(libB, List.of(type("p.Dup"))).getFirst();
        TypeName dup = TypeName.of("p.Dup");
        assertThat(types.findByBinaryName(dup, List.of(libA))).containsExactly(inA);
        assertThat(types.findByBinaryName(dup, List.of(libB))).containsExactly(inB);
        assertThat(types.findByBinaryName(dup, List.of(libA, libB))).containsExactly(inA, inB);
        assertThat(types.findByBinaryName(dup, List.of())).isEmpty();
        assertThat(types.findByBinaryName(dup, List.of(12345L))).isEmpty();
    }

    @Test
    void listsPackagesAndTypesByPackage() {
        types.insertAll(libA, List.of(type("a.b.X"), type("a.b.X$Inner"), type("a.c.Y"), type("Root")));
        types.insertAll(libB, List.of(type("z.Z")));
        assertThat(types.listPackages(List.of(libA))).containsExactly("", "a.b", "a.c");
        assertThat(types.findByPackage("a.b", List.of(libA)))
                .extracting(t -> t.name().binaryName()).containsExactly("a.b.X", "a.b.X$Inner");
        assertThat(types.findByPackage("a", List.of(libA))).isEmpty();
    }

    @Test
    void searchesDisplayNameSubstringIgnoringCaseWithLimit() {
        types.insertAll(libA, List.of(type("org.http.HttpServletRequest"), type("org.http.HttpClient"),
                type("org.other.Unrelated"), type("org.x.My_Type")));
        assertThat(types.searchByName("http", List.of(libA), 10))
                .extracting(t -> t.name().simpleName()).containsExactly("HttpClient", "HttpServletRequest");
        assertThat(types.searchByName("servletrequest", List.of(libA), 10)).hasSize(1);
        assertThat(types.searchByName("http", List.of(libA), 1)).hasSize(1);
        assertThat(types.searchByName("_", List.of(libA), 10))
                .extracting(t -> t.name().simpleName()).containsExactly("My_Type");
        assertThat(types.searchByName("http", List.of(libB), 10)).isEmpty();
    }

    @Test
    void listsNamesForInMemoryMatching() {
        List<IndexedType> stored = types.insertAll(libA, List.of(type("p.B"), type("p.A")));
        types.insertAll(libB, List.of(type("q.C")));
        assertThat(types.listNames(List.of(libA))).containsExactly(
                new TypeRef(stored.get(1).id(), libA, TypeName.of("p.A")),
                new TypeRef(stored.get(0).id(), libA, TypeName.of("p.B")));
        assertThat(types.listNames(List.of())).isEmpty();
    }

    @Test
    void findsTypesWithoutSourceDocs() {
        TypeDescriptor fromJavadoc = type("p.Generated").toBuilder().docSource(DocSource.JAVADOC)
                .entryPath("p/Generated.html").build();
        TypeDescriptor decompiled = type("p.Kotlin").toBuilder().docSource(DocSource.DECOMPILED)
                .entryPath("p/Kotlin.class").build();
        TypeDescriptor jdk = type("p.Jdk").toBuilder().docSource(DocSource.JDK).build();
        types.insertAll(libA, List.of(type("p.Normal"), fromJavadoc, decompiled, jdk));
        assertThat(types.findWithoutSources(libA))
                .extracting(t -> t.name().simpleName()).containsExactly("Generated", "Kotlin");
        assertThat(types.findWithoutSources(libB)).isEmpty();
        assertThat(types.countByArtifact(libA)).isEqualTo(4);
    }

    @Test
    void findsByIdsAcrossArtifacts() {
        IndexedType a = types.insertAll(libA, List.of(type("p.A"))).getFirst();
        IndexedType b = types.insertAll(libB, List.of(type("p.B"))).getFirst();
        assertThat(types.findByIds(List.of(b.id(), a.id()))).containsExactly(a, b);
        assertThat(types.findByIds(List.of())).isEmpty();
    }

    @Test
    void handlesLargeScopes() {
        IndexedType a = types.insertAll(libA, List.of(type("p.A"))).getFirst();
        List<Long> scope = new ArrayList<>();
        for (long id = 1000; id < 101_000; id++) {
            scope.add(id);
        }
        scope.add(libA);
        assertThat(types.findByBinaryName(TypeName.of("p.A"), scope)).containsExactly(a);
    }

    @Test
    void emptyInsertIsNoOp() {
        assertThat(types.insertAll(libA, List.of())).isEmpty();
    }
}
