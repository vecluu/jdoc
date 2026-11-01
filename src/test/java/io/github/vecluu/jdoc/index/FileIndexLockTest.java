package io.github.vecluu.jdoc.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.IndexException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("try")
class FileIndexLockTest {

    @TempDir
    Path tmp;

    @Test
    void createsLockFileAndReleases() {
        Path file = tmp.resolve("cache/lock");
        FileIndexLock lock = new FileIndexLock(file, Duration.ofSeconds(1));
        try (IndexLock.Lease lease = lock.acquire()) {
            assertThat(file).exists();
            assertThat(lock.isHeldByCurrentThread()).isTrue();
        }
        assertThat(lock.isHeldByCurrentThread()).isFalse();
        try (IndexLock.Lease again = lock.acquire()) {
            assertThat(lock.isHeldByCurrentThread()).isTrue();
        }
    }

    @Test
    void isReentrantForTheHoldingThread() {
        FileIndexLock lock = new FileIndexLock(tmp.resolve("lock"), Duration.ofMillis(200));
        try (IndexLock.Lease outer = lock.acquire()) {
            try (IndexLock.Lease inner = lock.acquire()) {
                assertThat(lock.isHeldByCurrentThread()).isTrue();
            }
            assertThat(lock.isHeldByCurrentThread()).isTrue();
        }
        assertThat(lock.isHeldByCurrentThread()).isFalse();
    }

    @Test
    void closingALeaseTwiceReleasesOnce() {
        FileIndexLock lock = new FileIndexLock(tmp.resolve("lock"), Duration.ofMillis(200));
        IndexLock.Lease outer = lock.acquire();
        IndexLock.Lease inner = lock.acquire();
        inner.close();
        inner.close();
        assertThat(lock.isHeldByCurrentThread()).isTrue();
        outer.close();
        assertThat(lock.isHeldByCurrentThread()).isFalse();
    }

    @Test
    void otherThreadTimesOutWhileHeld() throws Exception {
        FileIndexLock lock = new FileIndexLock(tmp.resolve("lock"), Duration.ofMillis(100));
        try (IndexLock.Lease lease = lock.acquire()) {
            CompletableFuture<Throwable> other = CompletableFuture.supplyAsync(() -> {
                try (IndexLock.Lease l = lock.acquire()) {
                    return null;
                } catch (RuntimeException e) {
                    return e;
                }
            });
            assertThat(other.get(5, TimeUnit.SECONDS)).isInstanceOf(IndexException.class)
                    .hasMessageContaining("timed out");
        }
    }

    @Test
    void secondInstanceOnSameFileTimesOutThenSucceeds() {
        Path file = tmp.resolve("lock");
        FileIndexLock first = new FileIndexLock(file, Duration.ofSeconds(1));
        FileIndexLock second = new FileIndexLock(file, Duration.ofMillis(100));
        try (IndexLock.Lease lease = first.acquire()) {
            assertThatThrownBy(second::acquire).isInstanceOf(IndexException.class);
            assertThat(second.isHeldByCurrentThread()).isFalse();
        }
        try (IndexLock.Lease lease = second.acquire()) {
            assertThat(second.isHeldByCurrentThread()).isTrue();
        }
    }

    @Test
    void rejectsNegativeTimeout() {
        assertThatThrownBy(() -> new FileIndexLock(tmp.resolve("lock"), Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
