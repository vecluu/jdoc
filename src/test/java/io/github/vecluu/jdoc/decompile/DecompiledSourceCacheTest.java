package io.github.vecluu.jdoc.decompile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.error.CacheIoException;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecompiledSourceCacheTest {

    private static final Gav GAV = Gav.of("org.acme", "widgets", "2.1");
    private static final TypeName OUTER = TypeName.of("org.acme.w.Outer");
    private static final TypeName INNER = TypeName.of("org.acme.w.Outer$Inner");

    @TempDir
    Path tmp;

    private Path jar;
    private CachePaths paths;
    private RecordingDecompiler decompiler;
    private DecompiledSourceCache cache;

    @BeforeEach
    void setUp() {
        jar = tmp.resolve("widgets-2.1.jar");
        paths = new CachePaths(tmp.resolve("cache"));
        decompiler = new RecordingDecompiler();
        cache = new DecompiledSourceCache(paths, decompiler);
    }

    @Test
    void cacheFileFollowsLayoutAndUsesTopLevelType() {
        Path expected = tmp.resolve("cache/decompiled/org/acme/widgets/2.1/org/acme/w/Outer.java");

        assertThat(cache.cacheFile(GAV, OUTER)).isEqualTo(expected);
        assertThat(cache.cacheFile(GAV, INNER)).isEqualTo(expected);
    }

    @Test
    void cacheFileOfClassifiedJarHasClassifierInVersionDirectory() {
        Gav classified = GAV.withClassifier("jdk8");

        assertThat(cache.cacheFile(classified, OUTER).toString()).contains("/widgets/2.1-jdk8/org/acme/w/Outer.java");
    }

    @Test
    void missDecompilesAndWritesFile() throws IOException {
        String text = cache.source(GAV, jar, OUTER);

        assertThat(text).isEqualTo("// org.acme.w.Outer\n");
        assertThat(decompiler.calls).containsExactly(OUTER);
        assertThat(Files.readString(cache.cacheFile(GAV, OUTER))).isEqualTo(text);
    }

    @Test
    void hitReadsFileWithoutDecompiling() throws IOException {
        Path file = cache.cacheFile(GAV, OUTER);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "class Cached {}");

        assertThat(cache.source(GAV, jar, INNER)).isEqualTo("class Cached {}");
        assertThat(decompiler.calls).isEmpty();
    }

    @Test
    void secondCallHitsCache() {
        cache.source(GAV, jar, OUTER);
        cache.source(GAV, jar, INNER);

        assertThat(decompiler.calls).hasSize(1);
    }

    @Test
    void preservesUnicodeContent() {
        decompiler.text = "String s = \"ä€\";\n";
        cache.source(GAV, jar, OUTER);

        assertThat(new DecompiledSourceCache(paths, new RecordingDecompiler()).source(GAV, jar, OUTER))
                .isEqualTo("String s = \"ä€\";\n");
    }

    @Test
    void writeLeavesNoTemporaryFiles() throws IOException {
        cache.source(GAV, jar, OUTER);

        try (Stream<Path> files = Files.list(cache.cacheFile(GAV, OUTER).getParent())) {
            assertThat(files).extracting(p -> p.getFileName().toString()).containsExactly("Outer.java");
        }
    }

    @Test
    void targetFileAppearsOnlyAfterDecompilingCompleted() {
        Path file = cache.cacheFile(GAV, OUTER);
        decompiler.duringCall = () -> assertThat(file).doesNotExist();

        cache.source(GAV, jar, OUTER);

        assertThat(file).exists();
    }

    @Test
    void decompilerFailurePropagatesAndWritesNothing() {
        decompiler.failure = new DecompilationException("Cannot decompile org.acme.w.Outer: boom");

        assertThatThrownBy(() -> cache.source(GAV, jar, OUTER))
                .isInstanceOf(DecompilationException.class)
                .hasMessageContaining("org.acme.w.Outer");
        assertThat(cache.cacheFile(GAV, OUTER)).doesNotExist();
    }

    @Test
    void unreadableCacheEntryBecomesCacheIoException() throws IOException {
        Files.createDirectories(cache.cacheFile(GAV, OUTER));

        assertThatThrownBy(() -> cache.source(GAV, jar, OUTER))
                .isInstanceOf(CacheIoException.class)
                .hasMessageContaining("Outer.java");
    }

    @Test
    void unwritableCacheBecomesCacheIoException() throws IOException {
        Files.createDirectories(tmp.resolve("cache/decompiled/org/acme/widgets"));
        Files.writeString(tmp.resolve("cache/decompiled/org/acme/widgets/2.1"), "not a directory");

        assertThatThrownBy(() -> cache.source(GAV, jar, OUTER)).isInstanceOf(CacheIoException.class);
    }

    /** Fake decompiler recording its calls. */
    private static final class RecordingDecompiler implements Decompiler {
        private final List<TypeName> calls = new ArrayList<>();
        private String text;
        private RuntimeException failure;
        private Runnable duringCall = () -> { };

        @Override
        public String decompile(Path jar, TypeName type) {
            calls.add(type);
            duringCall.run();
            if (failure != null) {
                throw failure;
            }
            return text != null ? text : "// " + type.topLevel().displayName() + "\n";
        }
    }
}
