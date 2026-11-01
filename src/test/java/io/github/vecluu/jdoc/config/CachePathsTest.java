package io.github.vecluu.jdoc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.CacheIoException;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachePathsTest {

    @TempDir
    Path tempDir;

    @Test
    void layout() {
        CachePaths paths = new CachePaths(Path.of("/c/jdoc"));
        Gav gav = Gav.of("org.apache.commons", "commons-lang3", "3.17.0");

        assertThat(paths.root()).isEqualTo(Path.of("/c/jdoc"));
        assertThat(paths.indexDb()).isEqualTo(Path.of("/c/jdoc/index.db"));
        assertThat(paths.lockFile()).isEqualTo(Path.of("/c/jdoc/lock"));
        assertThat(paths.moduleDirectory("ab12")).isEqualTo(Path.of("/c/jdoc/modules/ab12"));
        assertThat(paths.depsFile("ab12")).isEqualTo(Path.of("/c/jdoc/modules/ab12/deps.txt"));
        assertThat(paths.metaFile("ab12")).isEqualTo(Path.of("/c/jdoc/modules/ab12/meta.json"));
        assertThat(paths.decompiledDirectory(gav))
                .isEqualTo(Path.of("/c/jdoc/decompiled/org/apache/commons/commons-lang3/3.17.0"));
        assertThat(paths.decompiledSource(gav, TypeName.of("org.apache.commons.lang3.Pair$Inner")))
                .isEqualTo(Path.of("/c/jdoc/decompiled/org/apache/commons/commons-lang3/3.17.0/"
                        + "org/apache/commons/lang3/Pair.java"));
    }

    @Test
    void classifierGetsItsOwnDecompiledDirectory() {
        CachePaths paths = new CachePaths(Path.of("/c"));
        Gav gav = new Gav("io.netty", "netty-epoll", "4.1", Optional.of("linux"), "jar");
        assertThat(paths.decompiledDirectory(gav)).isEqualTo(Path.of("/c/decompiled/io/netty/netty-epoll/4.1-linux"));
    }

    @Test
    void rejectsUnsafeModuleKeysAndRelativeRoots() {
        CachePaths paths = new CachePaths(Path.of("/c"));
        assertThatThrownBy(() -> paths.moduleDirectory("../x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paths.moduleDirectory("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> paths.moduleDirectory(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CachePaths(Path.of("rel"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsDirectoriesOnlyOnDemand() {
        Path root = tempDir.resolve("cache");
        CachePaths paths = new CachePaths(root);
        Gav gav = Gav.of("g", "a", "1");
        assertThat(root).doesNotExist();

        assertThat(paths.createRoot()).isDirectory();
        assertThat(paths.createModuleDirectory("k1")).isDirectory().isEqualTo(paths.moduleDirectory("k1"));
        assertThat(paths.createDecompiledDirectory(gav)).isDirectory().isEqualTo(paths.decompiledDirectory(gav));
        assertThat(paths.createModuleDirectory("k1")).isDirectory();
    }

    @Test
    void creationFailureIsWrapped() throws IOException {
        Path blocker = Files.writeString(tempDir.resolve("file"), "x");
        CachePaths paths = new CachePaths(blocker);
        assertThatThrownBy(paths::createRoot).isInstanceOf(CacheIoException.class)
                .hasMessageContaining(blocker.toString());
    }
}
