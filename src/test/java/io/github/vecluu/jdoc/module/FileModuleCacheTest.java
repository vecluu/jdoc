package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.config.CachePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileModuleCacheTest {

    @TempDir
    Path tmp;

    private CachePaths paths;
    private FileModuleCache cache;

    @BeforeEach
    void setUp() {
        paths = new CachePaths(tmp.resolve("cache"));
        cache = new FileModuleCache(paths, new ModuleMetaCodec());
    }

    @Test
    void missWhenNothingStored() {
        assertThat(cache.readDependencyList("abc")).isEmpty();
        assertThat(cache.latestFor(tmp)).isEmpty();
    }

    @Test
    void commitPublishesDepsAndMeta() throws Exception {
        Path output = cache.newOutputFile("abc");
        assertThat(output).doesNotExist();
        assertThat(output.getParent()).isEqualTo(paths.moduleDirectory("abc"));
        Files.writeString(output, "deps");

        cache.commit(output, meta(tmp.resolve("app"), "abc", "2026-01-01T00:00:00Z"));

        assertThat(cache.readDependencyList("abc")).contains("deps");
        assertThat(paths.metaFile("abc")).exists();
        assertThat(output).doesNotExist();
        try (var files = Files.list(paths.moduleDirectory("abc"))) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("deps.txt", "meta.json");
        }
    }

    @Test
    void outputFilesAreUnique() {
        assertThat(cache.newOutputFile("k")).isNotEqualTo(cache.newOutputFile("k"));
    }

    @Test
    void discardRemovesOutputAndToleratesAbsence() throws Exception {
        Path output = cache.newOutputFile("k");
        Files.writeString(output, "partial");

        cache.discard(output);
        cache.discard(output);

        assertThat(output).doesNotExist();
        assertThat(cache.readDependencyList("k")).isEmpty();
    }

    @Test
    void latestForPicksNewestListOfSameModule() throws Exception {
        Path app = tmp.resolve("app");
        store("old", meta(app, "old", "2026-01-01T00:00:00Z"), "old deps");
        store("new", meta(app, "new", "2026-03-01T00:00:00Z"), "new deps");
        store("other", meta(tmp.resolve("other"), "other", "2026-05-01T00:00:00Z"), "other deps");

        assertThat(cache.latestFor(app)).hasValueSatisfying(list -> {
            assertThat(list.content()).isEqualTo("new deps");
            assertThat(list.meta().pomKey()).isEqualTo("new");
        });
    }

    @Test
    void latestForSkipsBrokenMetaAndMissingDeps() throws Exception {
        Path app = tmp.resolve("app");
        store("good", meta(app, "good", "2026-01-01T00:00:00Z"), "good deps");
        store("nodeps", meta(app, "nodeps", "2026-02-01T00:00:00Z"), "x");
        Files.delete(paths.depsFile("nodeps"));
        Files.createDirectories(paths.moduleDirectory("broken"));
        Files.writeString(paths.metaFile("broken"), "{broken");

        assertThat(cache.latestFor(app)).map(CachedDependencyList::content).contains("good deps");
    }

    private void store(String key, ModuleMeta meta, String content) throws Exception {
        Path output = cache.newOutputFile(key);
        Files.writeString(output, content);
        cache.commit(output, meta);
    }

    private static ModuleMeta meta(Path module, String key, String time) {
        return new ModuleMeta(module, key, List.of(), Instant.parse(time));
    }
}
