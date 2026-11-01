package io.github.vecluu.jdoc.module;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.config.CachePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileModuleSnapshotStoreTest {

    @TempDir
    Path tmp;

    @Test
    void writesAndReadsPerModuleDirectory() throws Exception {
        FileModuleSnapshotStore store = store();
        Path app = tmp.resolve("app");
        Path lib = tmp.resolve("lib");

        store.write(snapshot(app, "k1"));
        store.write(snapshot(lib, "k2"));
        store.write(snapshot(app, "k3"));

        assertThat(store.read(app).orElseThrow().analysis().key().value()).isEqualTo("k3");
        assertThat(store.read(lib).orElseThrow().analysis().key().value()).isEqualTo("k2");
        assertThat(store.read(tmp.resolve("other"))).isEmpty();
        assertThat(store.file(app).getFileName().toString()).matches("[0-9a-f]{16}\\.txt");
        try (var files = Files.list(store.file(app).getParent())) {
            assertThat(files).hasSize(2);
        }
    }

    @Test
    void snapshotOfAnotherDirectoryInTheSameFileIsIgnored() throws Exception {
        FileModuleSnapshotStore store = store();
        Path app = tmp.resolve("app");
        store.write(snapshot(tmp.resolve("collision"), "k1"));
        Files.createDirectories(store.file(app).getParent());
        Files.move(store.file(tmp.resolve("collision")), store.file(app));

        assertThat(store.read(app)).isEmpty();
    }

    @Test
    void corruptFileIsIgnored() throws Exception {
        FileModuleSnapshotStore store = store();
        Path app = tmp.resolve("app");
        Files.createDirectories(store.file(app).getParent());
        Files.writeString(store.file(app), "garbage");

        assertThat(store.read(app)).isEmpty();
    }

    @Test
    void writeFailureIsIgnored() throws Exception {
        Files.writeString(tmp.resolve("cache"), "a file where the cache directory should be");
        FileModuleSnapshotStore store = store();

        store.write(snapshot(tmp.resolve("app"), "k1"));

        assertThat(store.read(tmp.resolve("app"))).isEmpty();
    }

    private FileModuleSnapshotStore store() {
        return new FileModuleSnapshotStore(new CachePaths(tmp.resolve("cache")), new ModuleSnapshotCodec());
    }

    private static ModuleSnapshot snapshot(Path module, String key) {
        return new ModuleSnapshot(module, Path.of("/repo"), new PomAnalysis(new PomKey(key, List.of()),
                new Reactor(module, Set.of("g:a"))), Map.of(module.resolve("pom.xml"), PathStamp.ABSENT));
    }
}
