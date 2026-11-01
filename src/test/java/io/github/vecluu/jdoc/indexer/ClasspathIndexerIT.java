package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.bytecode.ClassFileReader;
import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.config.JdkInfo;
import io.github.vecluu.jdoc.config.JdocEnvironment;
import io.github.vecluu.jdoc.index.FileIndexLock;
import io.github.vecluu.jdoc.index.IndexLock;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.testsupport.FakeMavenRepo;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestEnvironments;
import io.github.vecluu.jdoc.testsupport.TestLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The whole indexing flow on real jars, a real SQLite file, the real lock and a miniature JDK src.zip. */
class ClasspathIndexerIT {

    private static final Gav UTIL = Gav.of("com.example", "util", "2.0-SNAPSHOT");

    @TempDir
    Path tmp;

    private CachePaths paths;
    private JdkInfo jdk;
    private IndexLock lock;
    private ModuleClasspath classpath;
    private ResolvedDependency sample;
    private ResolvedDependency util;

    @BeforeEach
    void setUp() throws IOException {
        JdocEnvironment environment = TestEnvironments.inTempDir(tmp);
        paths = environment.cachePaths();
        paths.createRoot();
        FakeMavenRepo repository = TestEnvironments.repository(environment);
        sample = repository.install(SampleLibrary.mixed());
        util = repository.install(TestLibrary.builder(UTIL)
                .source("com/example/util/Strings.java",
                        "package com.example.util; /** Strings. */ public class Strings { public static void x() {} }")
                .withJavadoc(false)
                .build());
        Path javaHome = Files.createDirectories(tmp.resolve("jdk/lib")).getParent();
        Path srcZip = Files.move(JdkSourceZipProducerTest.miniSrcZip(tmp), javaHome.resolve("lib/src.zip"));
        jdk = new JdkInfo(javaHome, 25, Optional.of(srcZip));
        lock = new FileIndexLock(paths.lockFile(), Duration.ofMinutes(1));
        classpath = new ModuleClasspath(tmp.resolve("work"), "key", List.of(sample, util),
                ResolutionStatus.RESOLVED, List.of());
    }

    /** One process-like indexer with its own database connection. */
    private final class Session implements AutoCloseable {
        final IndexerFixture fixture = new IndexerFixture(paths.indexDb());
        final ClasspathIndexer indexer = new ClasspathIndexer(fixture.store, fixture.artifactIndexer(),
                new DefaultJdkIndexer(fixture.store, JavaSourceParser::new, new ClassFileReader(),
                        new JdkPackageFilter(), 2, Clock.systemUTC()),
                jdk, lock, new NioFileStatter(), new DecompiledDirectoryEvictor(paths));

        IndexResult index(IndexOptions options) {
            return indexer.index(classpath, options);
        }

        @Override
        public void close() {
            fixture.close();
        }
    }

    private Path decompiledFile(Gav gav) throws IOException {
        Path file = paths.decompiledSource(gav, TypeName.of("p.Foo"));
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "class Foo {}");
    }

    @Test
    void indexesOnceThenReusesThenReindexesOnlyTheTouchedJar() throws IOException {
        try (Session session = new Session()) {
            IndexResult first = session.index(IndexOptions.indexCommand(false));
            assertThat(first.report().artifactsIndexed()).isEqualTo(3);
            assertThat(first.report().failures()).isEmpty();
            assertThat(first.classpath().artifacts().values()).containsExactly(sample.artifactKey(),
                    util.artifactKey(), ArtifactKey.jdk(25));
            assertThat(first.report().missingSources()).singleElement().satisfies(missing -> {
                assertThat(missing.artifact()).isEqualTo(sample.artifactKey());
                assertThat(missing.types()).extracting(TypeWithoutSources::name)
                        .containsExactly(SampleLibrary.GENERATED_MODEL, SampleLibrary.KOTLIN_UTILS);
            });
            assertThat(session.fixture.type(TypeName.of("java.util.Bag"), first.classpath().jdkArtifactId()
                    .orElseThrow()).descriptor().firstSentence()).isEqualTo("A bag.");

            IndexResult second = session.index(IndexOptions.lookup());
            assertThat(second.report().artifactsIndexed()).isZero();
            assertThat(second.report().artifactsReused()).isEqualTo(3);
            assertThat(second.classpath().artifactIds()).isEqualTo(first.classpath().artifactIds());

            Path staleUtil = decompiledFile(UTIL);
            Path keptSample = decompiledFile(sample.gav());
            Files.setLastModifiedTime(util.jar(), FileTime.fromMillis(Files.getLastModifiedTime(util.jar())
                    .toMillis() + 5_000));
            IndexResult third = session.index(IndexOptions.lookup());
            assertThat(third.report().artifactsIndexed()).isEqualTo(1);
            assertThat(third.report().artifactsReused()).isEqualTo(2);
            assertThat(staleUtil).doesNotExist();
            assertThat(paths.decompiledDirectory(UTIL)).doesNotExist();
            assertThat(keptSample).exists();
            long utilId = session.fixture.artifacts.findByKey(util.artifactKey()).orElseThrow().id();
            assertThat(third.classpath().artifactIds()).contains(utilId).doesNotContain(
                    first.classpath().artifactIds().get(1));
            assertThat(session.fixture.artifacts.findAll()).hasSize(3);
        }
    }

    @Test
    void forceReindexesTheModulesArtifactsButNotTheJdk() throws IOException {
        try (Session session = new Session()) {
            IndexResult first = session.index(IndexOptions.lookup());
            Path stale = decompiledFile(sample.gav());

            IndexResult forced = session.index(IndexOptions.indexCommand(true));

            assertThat(forced.report().artifactsIndexed()).isEqualTo(2);
            assertThat(forced.report().artifactsReused()).isEqualTo(1);
            assertThat(forced.classpath().jdkArtifactId()).isEqualTo(first.classpath().jdkArtifactId());
            assertThat(forced.classpath().artifactIds().getFirst())
                    .isNotEqualTo(first.classpath().artifactIds().getFirst());
            assertThat(stale).doesNotExist();
            assertThat(forced.report().typesIndexed()).isEqualTo(first.report().typesIndexed()
                    - session.fixture.types.countByArtifact(forced.classpath().jdkArtifactId().orElseThrow()));
        }
    }

    @Test
    void concurrentIndexersDoTheWorkOnce() throws Exception {
        int threads = 4;
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            sessions.add(new Session());
        }
        List<Callable<IndexResult>> tasks = new ArrayList<>();
        for (Session session : sessions) {
            tasks.add(() -> {
                try (session) {
                    start.await(30, TimeUnit.SECONDS);
                    return session.index(IndexOptions.lookup());
                }
            });
        }
        List<IndexResult> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (Future<IndexResult> future : pool.invokeAll(tasks, 2, TimeUnit.MINUTES)) {
                results.add(future.get());
            }
        }

        assertThat(results.stream().mapToInt(r -> r.report().artifactsIndexed()).sum()).isEqualTo(3);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.report().failures()).isEmpty();
            assertThat(result.classpath().artifactIds()).isEqualTo(results.getFirst().classpath().artifactIds());
        });
        try (Session session = new Session()) {
            List<IndexedArtifact> artifacts = session.fixture.artifacts.findAll();
            assertThat(artifacts).hasSize(3);
            for (IndexedArtifact artifact : artifacts) {
                assertThat(session.fixture.types.listNames(List.of(artifact.id())))
                        .extracting(ref -> ref.name()).doesNotHaveDuplicates();
            }
            assertThat(session.index(IndexOptions.lookup()).report().artifactsIndexed()).isZero();
        }
    }

    @Test
    void missingJarIsSkipped() throws IOException {
        Files.delete(util.jar());
        try (Session session = new Session()) {
            IndexResult result = session.index(IndexOptions.lookup());

            assertThat(result.report().failures()).isEmpty();
            assertThat(result.classpath().artifacts().values()).containsExactly(sample.artifactKey(),
                    ArtifactKey.jdk(25));
        }
    }

    @Test
    void corruptJarIsReportedWithoutFailingTheCall() throws IOException {
        Files.writeString(util.jar(), "not a zip");
        try (Session session = new Session()) {
            IndexResult result = session.index(IndexOptions.lookup());

            assertThat(result.report().failures()).singleElement().asString().startsWith("util 2.0-SNAPSHOT");
            assertThat(result.classpath().artifacts().values()).containsExactly(sample.artifactKey(),
                    ArtifactKey.jdk(25));
        }
    }
}
