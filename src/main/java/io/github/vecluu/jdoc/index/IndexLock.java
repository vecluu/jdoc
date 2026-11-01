package io.github.vecluu.jdoc.index;

/**
 * The exclusive lock held while indexing, shared by all jdoc processes using one cache.
 *
 * <p>Usage: acquire, re-check whether the work is still needed (another process may have done it while this one
 * waited), do the work, release:
 *
 * <pre>{@code
 * try (IndexLock.Lease lease = lock.acquire()) {
 *     if (stillNeeded()) { index(); }
 * }
 * }</pre>
 */
public interface IndexLock {

    /**
     * Blocks until the lock is held by this thread.
     *
     * @throws io.github.vecluu.jdoc.error.IndexException on timeout, interruption or lock-file I/O failure
     */
    Lease acquire();

    /** A held lock; closing releases it. */
    interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
