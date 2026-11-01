package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.config.JdkInfo;
import io.github.vecluu.jdoc.error.JarReadException;
import io.github.vecluu.jdoc.index.IndexLock;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClasspathIndexerTest {

    private static final Path HOME = Path.of("/jdk");
    private static final JdkInfo JDK = new JdkInfo(HOME, 25, Optional.of(HOME.resolve("lib/src.zip")));
    private static final Gav A = Gav.of("g", "a", "1.0");
    private static final Gav B = Gav.of("g", "b", "2.0");
    private static final Path JAR_A = Path.of("/m2/a-1.0.jar");
    private static final Path JAR_B = Path.of("/m2/b-2.0.jar");

    private final InMemoryIndexStore store = new InMemoryIndexStore();
    private final Map<Path, FileStamp> files = new HashMap<>();
    private final List<Gav> indexedArtifacts = new ArrayList<>();
    private final List<Gav> evicted = new ArrayList<>();
    private final Set<Gav> failing = new HashSet<>();
    private int jdkIndexed;
    private final CountingLock lock = new CountingLock();

    ClasspathIndexerTest() {
        files.put(JAR_A, new FileStamp(1000, 10));
        files.put(JAR_B, new FileStamp(2000, 20));
        files.put(JDK.srcZip().orElseThrow(), new FileStamp(5000, 50));
    }

    /** Lock fake: counts acquisitions and runs an optional action as "another process" right after acquiring. */
    private static final class CountingLock implements IndexLock {
        int acquired;
        Runnable onAcquire = () -> { };

        @Override
        public Lease acquire() {
            acquired++;
            onAcquire.run();
            return () -> { };
        }
    }

    private ArtifactIndexer artifactIndexer() {
        return (gav, jar, stamp) -> {
            if (failing.contains(gav)) {
                throw new JarReadException("corrupt " + jar);
            }
            indexedArtifacts.add(gav);
            return store.replace(artifact(ArtifactKey.maven(gav), jar, stamp), EntryProducer.of(List.of(
                    entry(gav.artifactId() + ".Documented", DocSource.SOURCES, Visibility.PUBLIC),
                    entry(gav.artifactId() + ".Generated", DocSource.JAVADOC, Visibility.PUBLIC),
                    entry(gav.artifactId() + ".Internal", DocSource.DECOMPILED, Visibility.PACKAGE),
                    entry(gav.artifactId() + ".Compiled", DocSource.DECOMPILED, Visibility.PUBLIC))));
        };
    }

    private JdkIndexer jdkIndexer() {
        return new JdkIndexer() {
            @Override
            public StoredArtifact index(JdkInfo jdk, FileStamp stamp) {
                jdkIndexed++;
                return store.replace(artifact(jdk.artifactKey(), indexedFile(jdk), stamp), EntryProducer.of(List.of(
                        entry("java.lang.Object", DocSource.JDK, Visibility.PUBLIC),
                        entry("java.lang.Compiled", DocSource.DECOMPILED, Visibility.PUBLIC))));
            }
        };
    }

    private static IndexedArtifact artifact(ArtifactKey key, Path jar, FileStamp stamp) {
        return IndexedArtifact.builder().key(key).jarPath(jar).jarMtime(stamp.mtimeMillis()).jarSize(stamp.size())
                .indexedAt(Instant.EPOCH).build();
    }

    private static TypeEntry entry(String name, DocSource docSource, Visibility visibility) {
        return new TypeEntry(Scanned.type(name).type().toBuilder().docSource(docSource).visibility(visibility)
                .build(), List.of());
    }

    private ClasspathIndexer indexer() {
        return new ClasspathIndexer(store, artifactIndexer(), jdkIndexer(), JDK, lock,
                file -> Optional.ofNullable(files.get(file)), evicted::add);
    }

    private static ModuleClasspath classpath(Path... jars) {
        List<ResolvedDependency> dependencies = new ArrayList<>();
        for (Path jar : jars) {
            dependencies.add(new ResolvedDependency(jar.equals(JAR_A) ? A : jar.equals(JAR_B) ? B
                    : Gav.of("g", jar.getFileName().toString(), "1"), Scope.COMPILE, jar));
        }
        return new ModuleClasspath(Path.of("/project"), "abc", dependencies, ResolutionStatus.STALE,
                List.of("maven failed"));
    }

    private IndexResult index(IndexOptions options) {
        return indexer().index(classpath(JAR_A, JAR_B), options);
    }

    @Test
    void indexesNewArtifactsAndTheJdkUnderTheLock() {
        IndexResult result = index(IndexOptions.lookup());

        assertThat(indexedArtifacts).containsExactly(A, B);
        assertThat(jdkIndexed).isEqualTo(1);
        assertThat(lock.acquired).isEqualTo(1);
        assertThat(evicted).isEmpty();
        assertThat(result.report().artifactsIndexed()).isEqualTo(3);
        assertThat(result.report().artifactsReused()).isZero();
        assertThat(result.report().typesIndexed()).isEqualTo(10);
        IndexedClasspath classpath = result.classpath();
        assertThat(classpath.artifactIds()).hasSize(3);
        assertThat(classpath.artifacts().values()).containsExactly(ArtifactKey.maven(A), ArtifactKey.maven(B),
                ArtifactKey.jdk(25));
        assertThat(classpath.jdkArtifactId()).contains(classpath.artifactIds().get(2));
        assertThat(classpath.label(classpath.artifactIds().getFirst())).contains("a 1.0");
        assertThat(classpath.status()).isEqualTo(ResolutionStatus.STALE);
        assertThat(classpath.warnings()).containsExactly("maven failed");
    }

    @Test
    void unchangedJarsAreReusedWithoutLocking() {
        IndexedClasspath first = index(IndexOptions.lookup()).classpath();
        indexedArtifacts.clear();

        IndexResult second = index(IndexOptions.lookup());

        assertThat(indexedArtifacts).isEmpty();
        assertThat(jdkIndexed).isEqualTo(1);
        assertThat(lock.acquired).isEqualTo(1);
        assertThat(second.report().artifactsReused()).isEqualTo(3);
        assertThat(second.report().artifactsIndexed()).isZero();
        assertThat(second.classpath().artifactIds()).isEqualTo(first.artifactIds());
    }

    @Test
    void changedMtimeOrSizeReindexesOnlyThatJarAndEvictsDecompiledSources() {
        index(IndexOptions.lookup());
        indexedArtifacts.clear();
        files.put(JAR_A, new FileStamp(1001, 10));

        IndexResult result = index(IndexOptions.lookup());
        assertThat(indexedArtifacts).containsExactly(A);
        assertThat(evicted).containsExactly(A);
        assertThat(result.report().artifactsIndexed()).isEqualTo(1);
        assertThat(result.report().artifactsReused()).isEqualTo(2);

        files.put(JAR_B, new FileStamp(2000, 21));
        index(IndexOptions.lookup());
        assertThat(indexedArtifacts).containsExactly(A, B);
    }

    @Test
    void movedJarIsReindexed() {
        index(IndexOptions.lookup());
        indexedArtifacts.clear();
        Path moved = Path.of("/other/a-1.0.jar");
        files.put(moved, files.get(JAR_A));

        indexer().index(new ModuleClasspath(Path.of("/p"), "k",
                List.of(new ResolvedDependency(A, Scope.COMPILE, moved)), ResolutionStatus.RESOLVED, List.of()),
                IndexOptions.lookup());

        assertThat(indexedArtifacts).containsExactly(A);
        assertThat(store.find(ArtifactKey.maven(A)).orElseThrow().jarPath()).isEqualTo(moved);
    }

    @Test
    void forceReindexesEveryArtifactButNotTheJdk() {
        index(IndexOptions.lookup());
        indexedArtifacts.clear();

        IndexResult result = index(IndexOptions.indexCommand(true));

        assertThat(indexedArtifacts).containsExactly(A, B);
        assertThat(evicted).containsExactly(A, B);
        assertThat(jdkIndexed).isEqualTo(1);
        assertThat(result.report().artifactsIndexed()).isEqualTo(2);
        assertThat(result.report().artifactsReused()).isEqualTo(1);
    }

    @Test
    void forceJdkReindexesTheJdk() {
        index(IndexOptions.lookup());

        index(new IndexOptions(false, true, false));

        assertThat(jdkIndexed).isEqualTo(2);
        assertThat(indexedArtifacts).containsExactly(A, B);
    }

    @Test
    void recheckAfterLockReusesWorkDoneByAnotherProcess() {
        lock.onAcquire = () -> {
            store.replace(artifact(ArtifactKey.maven(A), JAR_A, files.get(JAR_A)), EntryProducer.of(List.of()));
            store.replace(artifact(ArtifactKey.jdk(25), JDK.srcZip().orElseThrow(),
                    files.get(JDK.srcZip().orElseThrow())), EntryProducer.of(List.of()));
        };

        IndexResult result = index(IndexOptions.lookup());

        assertThat(indexedArtifacts).containsExactly(B);
        assertThat(jdkIndexed).isZero();
        assertThat(result.report().artifactsIndexed()).isEqualTo(1);
        assertThat(result.report().artifactsReused()).isEqualTo(2);
        assertThat(result.classpath().artifactIds()).hasSize(3);
    }

    @Test
    void missingJarsAreSkipped() {
        Path missing = Path.of("/m2/gone-1.jar");

        IndexResult result = indexer().index(classpath(JAR_A, missing), IndexOptions.lookup());

        assertThat(indexedArtifacts).containsExactly(A);
        assertThat(result.classpath().artifacts().values()).containsExactly(ArtifactKey.maven(A),
                ArtifactKey.jdk(25));
        assertThat(result.report().failures()).isEmpty();
    }

    @Test
    void missingJdkFileIsSkipped() {
        files.remove(JDK.srcZip().orElseThrow());

        IndexResult result = index(IndexOptions.lookup());

        assertThat(jdkIndexed).isZero();
        assertThat(result.classpath().jdkArtifactId()).isEmpty();
        assertThat(result.classpath().artifactIds()).hasSize(2);
    }

    @Test
    void duplicateDependenciesAreIndexedOnce() {
        IndexResult result = indexer().index(classpath(JAR_A, JAR_A), IndexOptions.lookup());

        assertThat(indexedArtifacts).containsExactly(A);
        assertThat(result.classpath().artifactIds()).hasSize(2);
    }

    @Test
    void failingArtifactIsReportedAndOlderIndexKept() {
        index(IndexOptions.lookup());
        long oldId = store.find(ArtifactKey.maven(A)).orElseThrow().id();
        failing.add(A);
        files.put(JAR_A, new FileStamp(9999, 10));

        IndexResult result = index(IndexOptions.lookup());

        assertThat(result.report().failures()).singleElement().asString().startsWith("a 1.0: corrupt");
        assertThat(result.classpath().artifactIds()).contains(oldId);
        assertThat(result.report().artifactsIndexed()).isZero();
    }

    @Test
    void failingNewArtifactIsLeftOutOfScope() {
        failing.add(B);

        IndexResult result = index(IndexOptions.lookup());

        assertThat(result.classpath().artifacts().values()).doesNotContain(ArtifactKey.maven(B));
        assertThat(result.report().failures()).hasSize(1);
    }

    @Test
    void missingSourcesReportListsApiTypesPerArtifactWithoutTheJdk() {
        IndexResult result = index(IndexOptions.indexCommand(false));

        assertThat(result.report().missingSources()).extracting(ArtifactWithoutSources::artifact)
                .containsExactly(ArtifactKey.maven(A), ArtifactKey.maven(B));
        assertThat(result.report().missingSources().getFirst().types()).containsExactly(
                new TypeWithoutSources(TypeName.of("a.Compiled"), DocSource.DECOMPILED),
                new TypeWithoutSources(TypeName.of("a.Generated"), DocSource.JAVADOC));
    }

    @Test
    void missingSourcesOnlyWhenRequested() {
        assertThat(index(IndexOptions.lookup()).report().missingSources()).isEmpty();
        assertThat(index(IndexOptions.indexCommand(false)).report().missingSources()).hasSize(2);
    }
}
