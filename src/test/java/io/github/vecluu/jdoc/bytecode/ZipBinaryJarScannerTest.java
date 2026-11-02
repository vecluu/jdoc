package io.github.vecluu.jdoc.bytecode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.JarReadException;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.testsupport.JarWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipBinaryJarScannerTest {

    @TempDir
    Path tmp;

    private final ZipBinaryJarScanner scanner = new ZipBinaryJarScanner();

    private Path jar() throws IOException {
        Map<String, byte[]> classes = InMemoryCompiler.compile(Map.of(
                "a/A.java", "package a; public class A { public class Inner {} Runnable r = new Runnable() {"
                        + " public void run() {} }; }",
                "a/package-info.java", "@Deprecated package a;",
                "b/B.java", "package b; public interface B { void run(); }",
                "module-info.java", "module m { exports a; exports b; }"));
        Map<String, byte[]> versioned = InMemoryCompiler.compile(
                "a/A.java", "package a; public class A { public void onlyInVersion() {} }");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        classes.forEach((name, bytes) -> entries.put(name + ".class", bytes));
        entries.put("META-INF/versions/21/a/A.class", versioned.get("a/A"));
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes());
        entries.put("a/readme.txt", "not a class".getBytes());
        entries.put("c/Broken.class", new byte[] {(byte) 0xCA, (byte) 0xFE, 0, 1});
        return JarWriter.writeEntries(entries, tmp.resolve("lib.jar"));
    }

    @Test
    void scansNamedClassesAndPackageInfos() throws IOException {
        List<ScannedClass> seen = new ArrayList<>();
        JarScanSummary summary = scanner.scan(jar(), seen::add);

        assertThat(seen).extracting(c -> c.type().name().binaryName())
                .containsExactlyInAnyOrder("a.A", "a.A$Inner", "b.B");
        assertThat(seen.stream().filter(c -> c.type().name().binaryName().equals("a.A")).findFirst().orElseThrow()
                .members()).extracting(m -> m.signature()).containsExactly("A()");
        assertThat(summary.typeCount()).isEqualTo(3);
        assertThat(summary.skippedEntries()).isEqualTo(1);
        assertThat(summary.packageInfoPackages()).containsExactly("a");
    }

    @Test
    void scanAllCollectsIntoList() throws IOException {
        assertThat(scanner.scanAll(jar())).hasSize(3);
    }

    @Test
    void readsSingleClass() throws IOException {
        Path jar = jar();

        assertThat(scanner.readClass(jar, TypeName.of("b.B"))).map(c -> c.members().getFirst().signature())
                .contains("void run()");
        assertThat(scanner.readClass(jar, TypeName.of("a.Missing"))).isEmpty();
        assertThat(scanner.readClass(jar, TypeName.of("a.A$1"))).isEmpty();
        assertThat(scanner.readClass(jar, TypeName.of("c.Broken"))).isEmpty();
    }

    @Test
    void failsForMissingOrCorruptJar() throws IOException {
        Path notAJar = Files.writeString(tmp.resolve("bad.jar"), "garbage");

        assertThatThrownBy(() -> scanner.scan(tmp.resolve("missing.jar"), c -> { }))
                .isInstanceOf(JarReadException.class);
        assertThatThrownBy(() -> scanner.scan(notAJar, c -> { })).isInstanceOf(JarReadException.class);
        assertThatThrownBy(() -> scanner.readClass(notAJar, TypeName.of("a.A")))
                .isInstanceOf(JarReadException.class);
    }
}
