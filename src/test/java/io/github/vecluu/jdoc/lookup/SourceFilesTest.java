package io.github.vecluu.jdoc.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.JarReadException;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.testsupport.JarWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFilesTest {

    @TempDir
    Path tmp;

    @Test
    void readsAndParsesEachEntryOnceAndCreatesTheParserLazily() throws Exception {
        Path jar = JarWriter.writeEntries(Map.of("p/A.java",
                "package p; /** Doc of A. */ public class A {}".getBytes(StandardCharsets.UTF_8)),
                tmp.resolve("s.jar"));
        List<String> reads = new ArrayList<>();
        JarEntryReader counting = (file, entry) -> {
            reads.add(entry);
            return new ZipJarEntryReader().readText(file, entry);
        };
        int[] parsersCreated = {0};
        try (SourceFiles files = new SourceFiles(counting, () -> {
            parsersCreated[0]++;
            return new JavaSourceParser();
        })) {
            assertThat(parsersCreated[0]).isZero();

            Optional<ParsedText> first = files.parse(jar, "p/A.java", DocSource.SOURCES);
            Optional<ParsedText> second = files.parse(jar, "p/A.java", DocSource.SOURCES);

            assertThat(first).isPresent();
            assertThat(second.orElseThrow()).isSameAs(first.orElseThrow());
            assertThat(first.get().file().type(TypeName.of("p.A")).orElseThrow().doc().description())
                    .isEqualTo("Doc of A.");
            assertThat(files.parse(jar, "p/Missing.java", DocSource.SOURCES)).isEmpty();
            assertThat(files.parse(jar, "p/Missing.java", DocSource.SOURCES)).isEmpty();
            assertThat(reads).containsExactly("p/A.java", "p/Missing.java");
            assertThat(parsersCreated[0]).isEqualTo(1);
        }
    }

    @Test
    void zipReaderReportsUnreadableJars() throws Exception {
        Path notAZip = Files.writeString(tmp.resolve("broken.jar"), "not a zip");
        ZipJarEntryReader reader = new ZipJarEntryReader();

        assertThatThrownBy(() -> reader.readText(notAZip, "a/B.java")).isInstanceOf(JarReadException.class);
        assertThatThrownBy(() -> reader.readText(tmp.resolve("missing.jar"), "a/B.java"))
                .isInstanceOf(JarReadException.class);
    }
}
