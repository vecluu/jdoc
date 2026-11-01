package io.github.vecluu.jdoc.decompile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.testsupport.JarWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VineflowerDecompilerTest {

    private final VineflowerDecompiler decompiler = new VineflowerDecompiler();

    @TempDir
    Path tmp;

    @Test
    void missingJarIsWrapped() {
        Path jar = tmp.resolve("missing.jar");

        assertThatThrownBy(() -> decompiler.decompile(jar, TypeName.of("a.B")))
                .isInstanceOf(DecompilationException.class)
                .hasMessageContaining("a.B")
                .hasMessageContaining("missing.jar");
    }

    @Test
    void notAJarIsWrapped() throws IOException {
        Path jar = Files.writeString(tmp.resolve("broken.jar"), "not a zip");

        assertThatThrownBy(() -> decompiler.decompile(jar, TypeName.of("a.B")))
                .isInstanceOf(DecompilationException.class)
                .hasMessageContaining("a.B");
    }

    @Test
    void missingClassEntryIsReported() throws IOException {
        Path jar = JarWriter.writeEntries(Map.of("a/Other.class", new byte[] {1}), tmp.resolve("lib.jar"));

        assertThatThrownBy(() -> decompiler.decompile(jar, TypeName.of("a.B$C")))
                .isInstanceOf(DecompilationException.class)
                .hasMessageContaining("a.B")
                .hasMessageContaining("a/B.class");
    }

    @Test
    void corruptClassIsWrapped() throws IOException {
        byte[] garbage = "definitely not a class file".getBytes(StandardCharsets.UTF_8);
        Path jar = JarWriter.writeEntries(Map.of("a/B.class", garbage), tmp.resolve("lib.jar"));

        assertThatThrownBy(() -> decompiler.decompile(jar, TypeName.of("a.B")))
                .isInstanceOf(DecompilationException.class)
                .hasMessageContaining("a.B");
    }
}
