package io.github.vecluu.jdoc.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The inter-process indexing file lock, and opening an index while another connection writes to it. */
@SuppressWarnings("try")
class IndexLockIT {

    private static final long HOLD_MILLIS = 700;
    /** Busy timeout of the index opens: far above {@link #MAX_OPEN}, so an open that waits for the writer fails. */
    private static final Duration BUSY_TIMEOUT = Duration.ofSeconds(10);
    /** Upper bound for opening a current index while another connection writes; well below the busy timeout. */
    private static final Duration MAX_OPEN = Duration.ofSeconds(3);
    /** How long the child process holds its write transaction; longer than {@link #MAX_OPEN}. */
    private static final long WRITER_HOLD_MILLIS = 6_000;

    @TempDir
    Path tmp;

    @Test
    void secondThreadWaitsForFirst() throws Exception {
        FileIndexLock lock = new FileIndexLock(tmp.resolve("lock"), Duration.ofSeconds(30));
        CountDownLatch held = new CountDownLatch(1);
        AtomicLong releasedAt = new AtomicLong();
        Thread holder = Thread.ofPlatform().start(() -> {
            try (IndexLock.Lease lease = lock.acquire()) {
                held.countDown();
                sleep(HOLD_MILLIS);
                releasedAt.set(System.nanoTime());
            }
        });
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        long acquiredAt;
        try (IndexLock.Lease lease = lock.acquire()) {
            acquiredAt = System.nanoTime();
        }
        holder.join(10_000);
        assertThat(releasedAt.get()).isNotZero();
        assertThat(acquiredAt).isGreaterThanOrEqualTo(releasedAt.get());
    }

    @Test
    void processWaitsForProcess() throws Exception {
        Path lockFile = tmp.resolve("cache/lock");
        Path marker = tmp.resolve("child-marker");
        Process child = startChild(LockHolderMain.class, lockFile, HOLD_MILLIS, marker);
        try {
            waitFor(marker, child);
            FileIndexLock lock = new FileIndexLock(lockFile, Duration.ofSeconds(30));
            long start = System.currentTimeMillis();
            try (IndexLock.Lease lease = lock.acquire()) {
                long acquiredAt = System.currentTimeMillis();
                List<String> lines = Files.readAllLines(marker);
                assertThat(lines).hasSize(2);
                long childReleased = Long.parseLong(lines.get(1).split(" ")[1]);
                assertThat(acquiredAt).isGreaterThanOrEqualTo(childReleased);
                assertThat(acquiredAt - start).isPositive();
            }
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(child.exitValue()).isZero();
        } finally {
            child.destroyForcibly();
        }
    }

    @Test
    void childProcessWaitsForThisProcess() throws Exception {
        Path lockFile = tmp.resolve("lock");
        Path marker = tmp.resolve("child-marker");
        FileIndexLock lock = new FileIndexLock(lockFile, Duration.ofSeconds(30));
        long releasedAt;
        Process child;
        try (IndexLock.Lease lease = lock.acquire()) {
            child = startChild(LockHolderMain.class, lockFile, 0, marker);
            sleep(HOLD_MILLIS);
            assertThat(marker).doesNotExist();
            releasedAt = System.currentTimeMillis();
        }
        try {
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(child.exitValue()).isZero();
            long childAcquired = Long.parseLong(Files.readAllLines(marker).getFirst().split(" ")[1]);
            assertThat(childAcquired).isGreaterThanOrEqualTo(releasedAt);
        } finally {
            child.destroyForcibly();
        }
    }

    /**
     * Opening an index whose schema is current must not wait for another connection's write transaction: the open
     * finishes well below the busy timeout while the writer still holds its lock, and reads see committed rows.
     */
    @Test
    void openingACurrentIndexDoesNotWaitForAWriteTransaction() {
        Path file = tmp.resolve("index.db");
        try (IndexDatabase writer = IndexDatabase.open(file, BUSY_TIMEOUT)) {
            new JdbcArtifactRepository(writer).save(IndexFixtures.artifact("org.example:committed:1.0"));
            writer.runInTransaction(() -> {
                new JdbcArtifactRepository(writer).save(IndexFixtures.artifact("org.example:pending:1.0"));
                long start = System.nanoTime();
                try (IndexDatabase reader = IndexDatabase.open(file, BUSY_TIMEOUT)) {
                    assertThat(new JdbcArtifactRepository(reader).findAll()).hasSize(1);
                    assertThat(reader.schemaVersion()).isEqualTo(Schema.VERSION);
                }
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(MAX_OPEN);
            });
        }
    }

    /** Like {@link #openingACurrentIndexDoesNotWaitForAWriteTransaction()}, with the writer in another process. */
    @Test
    void openingACurrentIndexDoesNotWaitForAnotherProcessWriteTransaction() throws Exception {
        Path file = tmp.resolve("index.db");
        IndexDatabase.open(file, BUSY_TIMEOUT).close();
        Path marker = tmp.resolve("writer-marker");
        Process child = startChild(WriteTransactionHolderMain.class, file, WRITER_HOLD_MILLIS, marker);
        try {
            waitFor(marker, child);
            long start = System.nanoTime();
            try (IndexDatabase reader = IndexDatabase.open(file, BUSY_TIMEOUT)) {
                assertThat(new JdbcArtifactRepository(reader).findAll()).isEmpty();
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            assertThat(Files.readAllLines(marker)).as("writer still holds its transaction").hasSize(1);
            assertThat(elapsed).isLessThan(MAX_OPEN);
            assertThat(child.waitFor(60, TimeUnit.SECONDS)).isTrue();
            assertThat(child.exitValue()).isZero();
        } finally {
            child.destroyForcibly();
        }
    }

    private static Process startChild(Class<?> main, Path file, long holdMillis, Path marker) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        return new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                main.getName(), file.toString(), Long.toString(holdMillis), marker.toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static void waitFor(Path marker, Process child) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!Files.exists(marker)) {
            assertThat(child.isAlive()).as("child process alive").isTrue();
            assertThat(System.currentTimeMillis()).as("child acquired lock in time").isLessThan(deadline);
            Thread.sleep(10);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
