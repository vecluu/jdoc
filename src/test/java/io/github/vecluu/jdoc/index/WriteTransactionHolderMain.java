package io.github.vecluu.jdoc.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Child process for {@link IndexLockIT}: {@code <indexDb> <holdMillis> <markerFile>}. Opens the index, starts a write
 * transaction, saves an artifact, writes {@code acquired <epochMillis>} to the marker file, holds the transaction,
 * then commits and appends {@code released <epochMillis>}.
 */
public final class WriteTransactionHolderMain {

    private WriteTransactionHolderMain() {
    }

    public static void main(String[] args) throws Exception {
        Path indexDb = Path.of(args[0]);
        long holdMillis = Long.parseLong(args[1]);
        Path marker = Path.of(args[2]);
        Path tmpMarker = marker.resolveSibling(marker.getFileName() + ".tmp");
        try (IndexDatabase database = IndexDatabase.open(indexDb, Duration.ofSeconds(60))) {
            database.runInTransaction(() -> {
                new JdbcArtifactRepository(database).save(IndexFixtures.artifact("org.example:held:1.0"));
                try {
                    Files.writeString(tmpMarker, "acquired " + System.currentTimeMillis() + "\n");
                    Files.move(tmpMarker, marker);
                    Thread.sleep(holdMillis);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        Files.writeString(marker, "released " + System.currentTimeMillis() + "\n", StandardOpenOption.APPEND);
    }
}
