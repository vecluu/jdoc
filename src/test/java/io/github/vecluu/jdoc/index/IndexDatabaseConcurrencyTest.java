package io.github.vecluu.jdoc.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.io.TempDir;

/** Several connections opening a brand-new {@code index.db} at the same moment must all succeed. */
class IndexDatabaseConcurrencyTest {

    private static final int THREADS = 8;

    @TempDir
    Path tmp;

    @RepeatedTest(5)
    void concurrentFirstOpenOfAFreshFileSucceedsEverywhere() throws Exception {
        Path file = tmp.resolve("index.db");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    try (IndexDatabase database = IndexDatabase.open(file, Duration.ofSeconds(30))) {
                        new JdbcArtifactRepository(database).findAll();
                        return database.schemaVersion();
                    }
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(Schema.VERSION);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
