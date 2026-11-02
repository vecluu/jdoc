package io.github.vecluu.jdoc.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.vecluu.jdoc.bytecode.ClassFileReader;
import io.github.vecluu.jdoc.config.JdkInfo;
import io.github.vecluu.jdoc.config.JdkInfoReader;
import io.github.vecluu.jdoc.index.IndexDatabase;
import io.github.vecluu.jdoc.index.JdbcArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcDocRepository;
import io.github.vecluu.jdoc.index.JdbcFailedArtifactRepository;
import io.github.vecluu.jdoc.index.JdbcMemberRepository;
import io.github.vecluu.jdoc.index.JdbcTypeRepository;
import io.github.vecluu.jdoc.indexer.DefaultJdkIndexer;
import io.github.vecluu.jdoc.indexer.JdkPackageFilter;
import io.github.vecluu.jdoc.indexer.NioFileStatter;
import io.github.vecluu.jdoc.indexer.SqliteIndexStore;
import io.github.vecluu.jdoc.indexer.StoredArtifact;
import io.github.vecluu.jdoc.query.MatchKind;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code jdoc find} with camel-hump patterns against the real JDK {@code src.zip} index, with a generous timing
 * bound on a warm index. Skipped if the JDK running the tests has no {@code src.zip}.
 */
@Tag("slow")
class JdkFindIT {

    private static final JdkInfo JDK = new JdkInfoReader().read(Path.of(System.getProperty("java.home")));
    private static final long BOUND_MILLIS = 1_000;

    @TempDir
    Path tmp;

    @Test
    void findsMembersByCamelHumpsQuickly() {
        assumeTrue(JDK.srcZip().isPresent(), "JDK has no lib/src.zip");
        try (IndexDatabase database = IndexDatabase.open(tmp.resolve("index.db"), Duration.ofSeconds(60))) {
            JdbcTypeRepository types = new JdbcTypeRepository(database);
            JdbcMemberRepository members = new JdbcMemberRepository(database);
            SqliteIndexStore store = new SqliteIndexStore(database, new JdbcArtifactRepository(database), types,
                    members, new JdbcFailedArtifactRepository(database), new JdbcDocRepository(database));
            DefaultJdkIndexer indexer = new DefaultJdkIndexer(store, JavaSourceParser::new, new ClassFileReader(),
                    new JdkPackageFilter(), DefaultJdkIndexer.defaultThreads(), Clock.systemUTC());
            StoredArtifact jdk = indexer.index(JDK, new NioFileStatter().stat(indexer.indexedFile(JDK)).orElseThrow());
            LookupScope scope = LookupScope.of(List.of(new ScopedArtifact(jdk.artifact().id(), jdk.artifact().key())));
            FindService find = new FindService(new ScopedTypes(types), members);

            find.find("oET", scope);
            find.find("gFB", scope);
            long start = System.nanoTime();
            FindResult orElseThrow = find.find("oET", scope);
            long millis = (System.nanoTime() - start) / 1_000_000;
            System.err.printf("JdkFindIT: find oET in %d ms (%d hits)%n", millis, orElseThrow.hits().size());

            assertThat(orElseThrow.hits()).extracting(FindHit::reference)
                    .contains("java.util.Optional#orElseThrow()");
            assertThat(orElseThrow.hits()).filteredOn(hit -> hit.reference().startsWith("java.util.Optional#"))
                    .allSatisfy(hit -> assertThat(hit.score().kind()).isEqualTo(MatchKind.CAMEL_HUMPS));
            assertThat(find.find("gOD", scope).hits()).extracting(FindHit::reference)
                    .anyMatch(reference -> reference.startsWith("java.util.Map#getOrDefault("));
            assertThat(millis).isLessThan(BOUND_MILLIS);
        }
    }
}
