package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecompiledDirectoryEvictorTest {

    @TempDir
    Path dir;

    @Test
    void deletesOnlyTheArtifactsDirectory() throws IOException {
        CachePaths paths = new CachePaths(dir);
        Gav gav = Gav.of("g", "a", "1.0-SNAPSHOT");
        Gav other = Gav.of("g", "a", "0.9");
        Path stale = paths.decompiledSource(gav, TypeName.of("p.Foo"));
        Path kept = paths.decompiledSource(other, TypeName.of("p.Foo"));
        Files.createDirectories(stale.getParent());
        Files.createDirectories(kept.getParent());
        Files.writeString(stale, "class Foo {}");
        Files.writeString(kept, "class Foo {}");

        new DecompiledDirectoryEvictor(paths).evict(gav);

        assertThat(paths.decompiledDirectory(gav)).doesNotExist();
        assertThat(kept).exists();
    }

    @Test
    void missingDirectoryIsFine() {
        new DecompiledDirectoryEvictor(new CachePaths(dir)).evict(Gav.of("g", "a", "1"));

        assertThat(dir.resolve("decompiled")).doesNotExist();
    }
}
