package io.github.vecluu.jdoc.decompile;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.error.CacheIoException;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decompiles each top-level type once and keeps the result in
 * {@code decompiled/<groupPath>/<artifactId>/<version>/<package path>/<Outer>.java} below the cache directory.
 *
 * <p>Files are written to a temporary file in the target directory and then moved into place atomically, so a
 * concurrent process either sees no file or the complete file. Two processes racing on the same type both decompile
 * and the last move wins; the content is identical because the decompiler output is deterministic.
 */
@Slf4j
@RequiredArgsConstructor
public final class DecompiledSourceCache implements DecompiledSourceProvider {

    private final CachePaths cachePaths;
    private final Decompiler decompiler;

    /** The cache file for a type (its top-level type's {@code .java} file); pure path computation. */
    public Path cacheFile(Gav gav, TypeName type) {
        return cachePaths.decompiledSource(gav, type.topLevel());
    }

    @Override
    public String source(Gav gav, Path jar, TypeName type) {
        Path file = cacheFile(gav, type);
        try {
            String cached = Files.readString(file, StandardCharsets.UTF_8);
            log.debug("Decompiled cache hit {}", file);
            return cached;
        } catch (NoSuchFileException e) {
            log.debug("Decompiled cache miss {}", file);
        } catch (IOException e) {
            throw new CacheIoException("Cannot read decompiled source " + file, e);
        }
        String text = decompiler.decompile(jar, type);
        write(file, text);
        return text;
    }

    private void write(Path file, String text) {
        Path directory = cachePaths.createDirectories(file.getParent());
        Path temp = null;
        try {
            temp = Files.createTempFile(directory, "." + file.getFileName(), ".tmp");
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            move(temp, file);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new CacheIoException("Cannot write decompiled source " + file, e);
        }
    }

    private static void move(Path temp, Path file) throws IOException {
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            log.debug("Cannot delete temporary file {}", temp, e);
        }
    }
}
