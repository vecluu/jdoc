package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.error.IndexException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link IndexLock} on a lock file: a {@link ReentrantLock} guards threads of this process, and an OS-level
 * {@link FileLock} on the file guards other processes. Acquisition waits up to a timeout, polling the file lock.
 *
 * <p>The lock is reentrant for the holding thread; only the outermost lease takes and releases the file lock. Use one
 * instance per lock file per process; a second instance in the same process still works (it polls until the
 * first releases) but without fair ordering.
 */
@Slf4j
public final class FileIndexLock implements IndexLock {

    /** Default time to wait for another indexer. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    private static final long MAX_POLL_MILLIS = 100;

    private final Path lockFile;
    private final Duration timeout;
    private final ReentrantLock threadGuard = new ReentrantLock(true);
    private FileChannel channel;
    private FileLock fileLock;

    /**
     * @param lockFile the lock file; created (with parent directories) when first acquired
     * @param timeout  how long {@link #acquire()} waits in total
     */
    public FileIndexLock(Path lockFile, Duration timeout) {
        this.lockFile = Objects.requireNonNull(lockFile, "lock file");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("negative lock timeout: " + timeout);
        }
    }

    @Override
    public Lease acquire() {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            if (!threadGuard.tryLock(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw timedOut();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexException("interrupted while waiting for index lock " + lockFile, e);
        }
        if (threadGuard.getHoldCount() > 1) {
            return new FileLease();
        }
        try {
            lockFile(deadline);
        } catch (RuntimeException e) {
            threadGuard.unlock();
            throw e;
        }
        return new FileLease();
    }

    /** True if the current thread holds this lock. */
    public boolean isHeldByCurrentThread() {
        return threadGuard.isHeldByCurrentThread();
    }

    private void lockFile(long deadline) {
        try {
            Files.createDirectories(lockFile.toAbsolutePath().getParent());
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            long pause = 5;
            while (true) {
                FileLock lock = tryLockChannel();
                if (lock != null) {
                    fileLock = lock;
                    return;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw timedOut();
                }
                log.debug("waiting for index lock {}", lockFile);
                Thread.sleep(Math.min(pause, TimeUnit.NANOSECONDS.toMillis(remaining) + 1));
                pause = Math.min(pause * 2, MAX_POLL_MILLIS);
            }
        } catch (IOException e) {
            closeChannel();
            throw new IndexException("cannot lock " + lockFile + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            closeChannel();
            Thread.currentThread().interrupt();
            throw new IndexException("interrupted while waiting for index lock " + lockFile, e);
        } catch (RuntimeException e) {
            closeChannel();
            throw e;
        }
    }

    private FileLock tryLockChannel() throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            // another FileIndexLock instance in this JVM holds the file
            return null;
        }
    }

    private void release() {
        if (!threadGuard.isHeldByCurrentThread()) {
            throw new IllegalStateException("index lock " + lockFile + " is not held by this thread");
        }
        try {
            if (threadGuard.getHoldCount() == 1) {
                releaseFileLock();
            }
        } finally {
            threadGuard.unlock();
        }
    }

    private void releaseFileLock() {
        try {
            if (fileLock != null) {
                fileLock.release();
            }
        } catch (IOException e) {
            throw new IndexException("cannot release index lock " + lockFile + ": " + e.getMessage(), e);
        } finally {
            fileLock = null;
            closeChannel();
        }
    }

    private void closeChannel() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                log.warn("cannot close index lock file {}: {}", lockFile, e.getMessage());
            }
            channel = null;
        }
    }

    /** A lease that releases at most once, even if closed repeatedly. */
    private final class FileLease implements Lease {
        private boolean closed;

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                release();
            }
        }
    }

    private IndexException timedOut() {
        return new IndexException("timed out after " + timeout.toSeconds() + "s waiting for index lock " + lockFile
                + " (another jdoc process is indexing)");
    }
}
