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

@SuppressWarnings("try")
class IndexLockIT {

    private static final long HOLD_MILLIS = 700;

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
        Process child = startChild(lockFile, HOLD_MILLIS, marker);
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
            child = startChild(lockFile, 0, marker);
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

    private static Process startChild(Path lockFile, long holdMillis, Path marker) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        return new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                LockHolderMain.class.getName(), lockFile.toString(), Long.toString(holdMillis), marker.toString())
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
