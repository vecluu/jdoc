package io.github.vecluu.jdoc.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Child process for {@link IndexLockIT}: {@code <lockFile> <holdMillis> <markerFile>}. Acquires the lock, writes
 * {@code acquired <epochMillis>} to the marker file, holds the lock, then appends {@code released <epochMillis>}.
 */
@SuppressWarnings("try")
public final class LockHolderMain {

    private LockHolderMain() {
    }

    public static void main(String[] args) throws Exception {
        Path lockFile = Path.of(args[0]);
        long holdMillis = Long.parseLong(args[1]);
        Path marker = Path.of(args[2]);
        FileIndexLock lock = new FileIndexLock(lockFile, Duration.ofSeconds(60));
        try (IndexLock.Lease lease = lock.acquire()) {
            Files.writeString(marker.resolveSibling(marker.getFileName() + ".tmp"),
                    "acquired " + System.currentTimeMillis() + "\n");
            Files.move(marker.resolveSibling(marker.getFileName() + ".tmp"), marker);
            Thread.sleep(holdMillis);
            Files.writeString(marker, "acquired-then-released " + System.currentTimeMillis() + "\n",
                    StandardOpenOption.APPEND);
        }
    }
}
