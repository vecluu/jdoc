package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.index.IndexDatabase;
import io.github.vecluu.jdoc.index.FailedArtifact;
import io.github.vecluu.jdoc.index.JdbcArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcDocRepository;
import io.github.vecluu.jdoc.index.JdbcFailedArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcMemberRepository;
import io.github.vecluu.jdoc.index.JdbcTypeRepository;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteIndexStoreTest {

    private static final ArtifactKey KEY = ArtifactKey.maven(Gav.of("g", "a", "1"));

    @TempDir
    Path dir;

    private IndexDatabase database;
    private SqliteIndexStore store;
    private JdbcTypeRepository types;
    private JdbcMemberRepository members;
    private JdbcDocRepository docs;

    @BeforeEach
    void open() {
        database = IndexDatabase.open(dir.resolve("index.db"), Duration.ofSeconds(5));
        types = new JdbcTypeRepository(database);
        members = new JdbcMemberRepository(database);
        docs = new JdbcDocRepository(database);
        store = new SqliteIndexStore(database, new JdbcArtifactRepository(database), types, members,
                new JdbcFailedArtifactRepository(database), docs);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private static IndexedArtifact artifact(long size) {
        return IndexedArtifact.builder().key(KEY).jarPath(Path.of("/a.jar")).jarMtime(1).jarSize(size)
                .indexedAt(Instant.EPOCH).build();
    }

    private static TypeEntry entry(String name, DocSource docSource, String... methods) {
        return TypeEntry.undocumented(Scanned.type(name, methods).type().toBuilder().docSource(docSource).build(),
                Scanned.type(name, methods).members());
    }

    @Test
    void writesBatchesAndCounts() {
        StoredArtifact stored = store.replace(artifact(1), sink -> {
            sink.types(List.of(entry("p.A", DocSource.SOURCES, "a", "b")));
            sink.types(List.of());
            sink.types(List.of(entry("p.B", DocSource.JAVADOC, "c"), entry("p.C", DocSource.DECOMPILED)));
        });

        assertThat(stored.typeCount()).isEqualTo(3);
        assertThat(stored.memberCount()).isEqualTo(3);
        assertThat(store.find(KEY)).contains(stored.artifact());
        assertThat(store.typesWithoutSources(stored.artifact().id())).extracting(t -> t.descriptor().name())
                .containsExactlyInAnyOrder(TypeName.of("p.B"), TypeName.of("p.C"));
    }

    @Test
    void replaceDropsTheOldIndex() {
        long oldId = store.replace(artifact(1), EntryProducer.of(List.of(entry("p.Old", DocSource.SOURCES))))
                .artifact().id();

        StoredArtifact stored = store.replace(artifact(2),
                EntryProducer.of(List.of(entry("p.New", DocSource.SOURCES))));

        assertThat(stored.artifact().id()).isNotEqualTo(oldId);
        assertThat(types.countByArtifact(oldId)).isZero();
        assertThat(types.findByBinaryName(TypeName.of("p.New"), List.of(stored.artifact().id())))
                .extracting(IndexedType::artifactId).containsExactly(stored.artifact().id());
    }

    @Test
    void failingProducerLeavesTheOldIndexIntact() {
        StoredArtifact old = store.replace(artifact(1), EntryProducer.of(List.of(entry("p.Old", DocSource.SOURCES))));

        assertThatThrownBy(() -> store.replace(artifact(2), sink -> {
            sink.types(List.of(entry("p.New", DocSource.SOURCES)));
            throw new IllegalStateException("parse failure");
        })).hasMessage("parse failure");

        assertThat(store.find(KEY)).contains(old.artifact());
        assertThat(types.countByArtifact(old.artifact().id())).isEqualTo(1);
    }

    @Test
    void storesTypeMemberAndPackageDocsAndSkipsMissingOnes() {
        TypeEntry a = entry("p.A", DocSource.SOURCES, "a", "b", "c");
        TypeEntry documented = new TypeEntry(a.type(), doc("Type A."), List.of(
                new MemberEntry(a.memberDescriptors().get(0), doc("Does a.")),
                MemberEntry.undocumented(a.memberDescriptors().get(1)),
                new MemberEntry(a.memberDescriptors().get(2), doc("Does c."))));
        StoredArtifact stored = store.replace(artifact(1), sink -> {
            sink.types(List.of(documented, entry("p.B", DocSource.SOURCES, "d")));
            sink.packageDoc("p", doc("Package p."));
        });

        long artifactId = stored.artifact().id();
        IndexedType typeA = types.findByBinaryName(TypeName.of("p.A"), List.of(artifactId)).getFirst();
        IndexedType typeB = types.findByBinaryName(TypeName.of("p.B"), List.of(artifactId)).getFirst();
        assertThat(docs.typeDoc(typeA.id())).contains(doc("Type A."));
        assertThat(docs.typeDoc(typeB.id())).isEmpty();
        List<IndexedMember> membersOfA = members.membersOf(typeA.id());
        assertThat(membersOfA).extracting(member -> docs.memberDoc(member.id()))
                .containsExactly(Optional.of(doc("Does a.")), Optional.empty(), Optional.of(doc("Does c.")));
        assertThat(docs.memberDoc(members.membersOf(typeB.id()).getFirst().id())).isEmpty();
        assertThat(docs.packageDoc(artifactId, "p")).contains(doc("Package p."));
    }

    @Test
    void replacingAnArtifactDropsItsDocs() {
        TypeEntry a = entry("p.A", DocSource.SOURCES, "a");
        long oldId = store.replace(artifact(1), sink -> {
            sink.types(List.of(new TypeEntry(a.type(), doc("Old."),
                    List.of(new MemberEntry(a.memberDescriptors().getFirst(), doc("Old a."))))));
            sink.packageDoc("p", doc("Old p."));
        }).artifact().id();
        IndexedType oldType = types.findByBinaryName(TypeName.of("p.A"), List.of(oldId)).getFirst();
        long oldMember = members.membersOf(oldType.id()).getFirst().id();

        store.replace(artifact(2), EntryProducer.of(List.of(a)));

        assertThat(docs.typeDoc(oldType.id())).isEmpty();
        assertThat(docs.memberDoc(oldMember)).isEmpty();
        assertThat(docs.packageDoc(oldId, "p")).isEmpty();
    }

    private static RenderedDoc doc(String text) {
        return new RenderedDoc(text, text, List.of("@since 1"), false);
    }

    @Test
    void remembersFailuresUntilTheArtifactIsReplaced() {
        FailedArtifact failure = new FailedArtifact(KEY, Path.of("/a.jar"), 1, 7, "zip END header not found");
        store.recordFailure(failure);
        assertThat(store.findFailure(KEY)).contains(failure);

        store.replace(artifact(7), EntryProducer.of(List.of()));

        assertThat(store.findFailure(KEY)).isEmpty();
    }

    @Test
    void failingReplaceKeepsTheRememberedFailure() {
        FailedArtifact failure = new FailedArtifact(KEY, Path.of("/a.jar"), 1, 7, "broken");
        store.recordFailure(failure);

        assertThatThrownBy(() -> store.replace(artifact(7), sink -> {
            throw new IllegalStateException("parse failure");
        })).hasMessage("parse failure");

        assertThat(store.findFailure(KEY)).contains(failure);
    }
}
