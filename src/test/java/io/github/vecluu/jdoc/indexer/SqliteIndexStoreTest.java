package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.index.IndexDatabase;
import io.github.vecluu.jdoc.index.FailedArtifact;
import io.github.vecluu.jdoc.index.JdbcArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcFailedArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcMemberRepository;
import io.github.vecluu.jdoc.index.JdbcTypeRepository;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    @BeforeEach
    void open() {
        database = IndexDatabase.open(dir.resolve("index.db"), Duration.ofSeconds(5));
        types = new JdbcTypeRepository(database);
        store = new SqliteIndexStore(database, new JdbcArtifactRepository(database), types,
                new JdbcMemberRepository(database), new JdbcFailedArtifactRepository(database));
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
        return new TypeEntry(Scanned.type(name, methods).type().toBuilder().docSource(docSource).build(),
                Scanned.type(name, methods).members());
    }

    @Test
    void writesBatchesAndCounts() {
        StoredArtifact stored = store.replace(artifact(1), sink -> {
            sink.accept(List.of(entry("p.A", DocSource.SOURCES, "a", "b")));
            sink.accept(List.of());
            sink.accept(List.of(entry("p.B", DocSource.JAVADOC, "c"), entry("p.C", DocSource.DECOMPILED)));
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
            sink.accept(List.of(entry("p.New", DocSource.SOURCES)));
            throw new IllegalStateException("parse failure");
        })).hasMessage("parse failure");

        assertThat(store.find(KEY)).contains(old.artifact());
        assertThat(types.countByArtifact(old.artifact().id())).isEqualTo(1);
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
