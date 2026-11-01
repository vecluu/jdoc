package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.config.CachePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ModuleSnapshotStore} in {@code <cache>/module-snapshots/<hash>.txt}, where the hash is a 64-bit FNV-1a of
 * the module directory (no {@code MessageDigest}, which would cost its provider set-up on every call). The file
 * repeats the module directory, so a hash collision only makes the two modules overwrite each other's snapshot.
 * Files are published with an atomic rename, so concurrent jdoc processes never read half a snapshot.
 */
@Slf4j
@RequiredArgsConstructor
public final class FileModuleSnapshotStore implements ModuleSnapshotStore {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final CachePaths paths;
    private final ModuleSnapshotCodec codec;

    @Override
    public Optional<ModuleSnapshot> read(Path moduleDirectory) {
        Path file = file(moduleDirectory);
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            log.debug("cannot read module snapshot {}: {}", file, e.getMessage());
            return Optional.empty();
        }
        return codec.decode(text).filter(snapshot -> snapshot.moduleDirectory().equals(moduleDirectory));
    }

    @Override
    public void write(ModuleSnapshot snapshot) {
        Optional<String> text = codec.encode(snapshot);
        if (text.isEmpty()) {
            log.debug("not storing a module snapshot for {}: a path contains a tab or line break",
                    snapshot.moduleDirectory());
            return;
        }
        Path file = file(snapshot.moduleDirectory());
        Path temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, text.get(), StandardCharsets.UTF_8);
            move(temp, file);
        } catch (IOException e) {
            log.debug("cannot store module snapshot {}: {}", file, e.getMessage());
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                log.debug("cannot delete {}", temp);
            }
        }
    }

    /** The snapshot file of a module directory. */
    Path file(Path moduleDirectory) {
        return paths.moduleSnapshotsDirectory()
                .resolve(HexFormat.of().toHexDigits(fnv1a(moduleDirectory.toString())) + ".txt");
    }

    private static long fnv1a(String text) {
        long hash = FNV_OFFSET;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
