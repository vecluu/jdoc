package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.model.Gav;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Deletes {@code decompiled/<g>/<a>/<v>/} ({@link CachePaths#decompiledDirectory(Gav)}) recursively. */
@Slf4j
@RequiredArgsConstructor
public final class DecompiledDirectoryEvictor implements DecompiledCacheEvictor {

    private final CachePaths paths;

    @Override
    public void evict(Gav gav) {
        Path directory = paths.decompiledDirectory(gav);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : all) {
                Files.deleteIfExists(path);
            }
            log.debug("deleted decompiled sources of {} in {}", gav, directory);
        } catch (IOException e) {
            log.warn("cannot delete stale decompiled sources in {}: {}", directory, e.toString());
        }
    }
}
