package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.bytecode.ClassFileReader;
import io.github.vecluu.jdoc.config.JdkInfo;
import io.github.vecluu.jdoc.config.JdkInfoReader;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Indexes the real JDK running the tests ({@code src.zip}, and the {@code jrt:/} fallback) into a temp database. */
class JdkIndexerIT {

    private static final JdkInfo JDK = new JdkInfoReader().read(Path.of(System.getProperty("java.home")));

    @TempDir
    Path tmp;

    private static StoredArtifact index(IndexerFixture fixture, JdkInfo jdk) {
        DefaultJdkIndexer indexer = new DefaultJdkIndexer(fixture.store, JavaSourceParser::new,
                new ClassFileReader(), new JdkPackageFilter(), DefaultJdkIndexer.defaultThreads(), Clock.systemUTC());
        FileStamp stamp = new NioFileStatter().stat(indexer.indexedFile(jdk)).orElseThrow();
        long start = System.nanoTime();
        StoredArtifact stored = indexer.index(jdk, stamp);
        System.err.printf("JdkIndexerIT: %s indexed in %d ms (%d types, %d members, %d threads)%n",
                jdk.srcZip().isPresent() ? "src.zip" : "jrt:/", (System.nanoTime() - start) / 1_000_000,
                stored.typeCount(), stored.memberCount(), DefaultJdkIndexer.defaultThreads());
        return stored;
    }

    @Test
    void indexesSrcZip() {
        assumeTrue(JDK.srcZip().isPresent(), "JDK has no lib/src.zip");
        try (IndexerFixture fixture = new IndexerFixture(tmp.resolve("index.db"))) {
            StoredArtifact stored = index(fixture, JDK);
            long id = stored.artifact().id();

            assertThat(stored.artifact().key()).isEqualTo(JDK.artifactKey());
            assertThat(stored.typeCount()).isGreaterThan(5_000);

            TypeDescriptor optional = fixture.type(TypeName.of("java.util.Optional"), id).descriptor();
            assertThat(optional.docSource()).isEqualTo(DocSource.JDK);
            assertThat(optional.entryPath()).isEqualTo("java.base/java/util/Optional.java");
            assertThat(optional.firstSentence()).startsWith("A container object which may or may not contain");
            List<String> signatures = fixture.members(TypeName.of("java.util.Optional"), id).stream()
                    .map(IndexedMember::descriptor).map(MemberDescriptor::signature).toList();
            assertThat(signatures).contains("static <T> Optional<T> of(T value)", "T get()", "boolean isPresent()");

            TypeDescriptor entry = fixture.type(TypeName.of("java.util.Map$Entry"), id).descriptor();
            assertThat(entry.kind()).isEqualTo(TypeKind.INTERFACE);
            assertThat(fixture.type(TypeName.of("java.util.concurrent.TimeUnit"), id).descriptor().kind())
                    .isEqualTo(TypeKind.ENUM);
            assertThat(fixture.type(TypeName.of("java.lang.FunctionalInterface"), id).descriptor().kind())
                    .isEqualTo(TypeKind.ANNOTATION);
            assertThat(fixture.type(TypeName.of("java.util.ArrayList"), id).descriptor().supertypes())
                    .contains(TypeName.of("java.util.AbstractList"), TypeName.of("java.util.List"),
                            TypeName.of("java.util.RandomAccess"));
            assertThat(fixture.type(TypeName.of("java.util.HashMap"), id).descriptor().supertypes())
                    .containsExactly(TypeName.of("java.util.AbstractMap"), TypeName.of("java.util.Map"),
                            TypeName.of("java.lang.Cloneable"), TypeName.of("java.io.Serializable"));
            assertThat(fixture.types.findByPackage("jdk.internal.misc", List.of(id))).isEmpty();
            assertThat(fixture.types.findByPackage("sun.misc", List.of(id))).isEmpty();
            assertThat(fixture.types.findByPackage("java.net.http", List.of(id))).isNotEmpty();
        }
    }

    @Test
    void fallsBackToJrtBytecodeWithoutSrcZip() {
        JdkInfo withoutSources = new JdkInfo(JDK.javaHome(), JDK.featureVersion(), Optional.empty());
        try (IndexerFixture fixture = new IndexerFixture(tmp.resolve("jrt.db"))) {
            StoredArtifact stored = index(fixture, withoutSources);
            long id = stored.artifact().id();

            assertThat(stored.artifact().jarPath()).isEqualTo(JDK.javaHome().resolve("lib/modules"));
            TypeDescriptor optional = fixture.type(TypeName.of("java.util.Optional"), id).descriptor();
            assertThat(optional.docSource()).isEqualTo(DocSource.DECOMPILED);
            assertThat(optional.entryPath()).isEqualTo("java/util/Optional.class");
            assertThat(fixture.members(TypeName.of("java.util.Optional"), id)).extracting(m -> m.descriptor().name())
                    .contains("of", "get", "isPresent");
            assertThat(fixture.types.findByPackage("jdk.internal.misc", List.of(id))).isEmpty();
        }
    }
}
