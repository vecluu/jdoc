package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.error.CacheIoException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ModuleCache} on the cache directory. Files are published with an atomic rename, so concurrent jdoc
 * processes never see a half-written {@code deps.txt} or {@code meta.json}.
 */
@Slf4j
@RequiredArgsConstructor
public final class FileModuleCache implements ModuleCache {

    private final CachePaths paths;
    private final ModuleMetaCodec codec;

    @Override
    public Optional<String> readDependencyList(String pomKey) {
        return readIfPresent(paths.depsFile(pomKey));
    }

    @Override
    public Path newOutputFile(String pomKey) {
        return paths.createModuleDirectory(pomKey).resolve("deps.txt." + UUID.randomUUID() + ".tmp");
    }

    @Override
    public void commit(Path outputFile, ModuleMeta meta) {
        Path directory = paths.createModuleDirectory(meta.pomKey());
        Path metaTemp = directory.resolve("meta.json." + UUID.randomUUID() + ".tmp");
        try {
            Files.writeString(metaTemp, codec.encode(meta), StandardCharsets.UTF_8);
            move(outputFile, paths.depsFile(meta.pomKey()));
            move(metaTemp, paths.metaFile(meta.pomKey()));
        } catch (IOException e) {
            discard(metaTemp);
            throw new CacheIoException("cannot store resolved classpath in " + directory + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void discard(Path outputFile) {
        try {
            Files.deleteIfExists(outputFile);
        } catch (IOException e) {
            log.debug("cannot delete {}: {}", outputFile, e.getMessage());
        }
    }

    @Override
    public Optional<CachedDependencyList> latestFor(Path moduleDirectory) {
        Path modules = paths.modulesDirectory();
        if (!Files.isDirectory(modules)) {
            return Optional.empty();
        }
        List<Path> directories;
        try (Stream<Path> children = Files.list(modules)) {
            directories = children.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            throw new CacheIoException("cannot list " + modules + ": " + e.getMessage(), e);
        }
        record Candidate(ModuleMeta meta, Path directory) {
        }
        return directories.stream()
                .flatMap(directory -> readIfPresent(directory.resolve("meta.json")).flatMap(codec::decode)
                        .filter(meta -> meta.moduleDirectory().equals(moduleDirectory))
                        .map(meta -> new Candidate(meta, directory)).stream())
                .sorted(Comparator.comparing((Candidate candidate) -> candidate.meta().resolvedAt()).reversed())
                .flatMap(candidate -> readIfPresent(candidate.directory().resolve("deps.txt"))
                        .map(content -> new CachedDependencyList(candidate.meta(), content)).stream())
                .findFirst();
    }

    private static Optional<String> readIfPresent(Path file) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new CacheIoException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
