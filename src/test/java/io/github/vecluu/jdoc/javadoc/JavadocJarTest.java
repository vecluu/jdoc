package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.JarReadException;
import io.github.vecluu.jdoc.testsupport.JarWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavadocJarTest {

    @TempDir
    Path tmp;

    @Test
    void readsPagesWithDeclaredCharset() throws IOException {
        Path jar = JarWriter.writeEntries(Map.of(
                "a/A.html", "<html><head><meta charset=\"utf-8\"></head><body>Grüße</body></html>"
                        .getBytes(StandardCharsets.UTF_8),
                "a/B.html", "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\">"
                        .concat("</head><body>Grüße</body></html>").getBytes(StandardCharsets.ISO_8859_1),
                "a/C.html", "<p>plain é</p>".getBytes(StandardCharsets.UTF_8)), tmp.resolve("x-javadoc.jar"));

        try (JavadocJar javadoc = JavadocJar.open(jar)) {
            assertThat(javadoc.page("a/A.html")).hasValueSatisfying(html -> assertThat(html).contains("Grüße"));
            assertThat(javadoc.page("a/B.html")).hasValueSatisfying(html -> assertThat(html).contains("Grüße"));
            assertThat(javadoc.page("a/C.html")).contains("<p>plain é</p>");
            assertThat(javadoc.page("a/Missing.html")).isEmpty();
            assertThat(javadoc.page("a/")).isEmpty();
        }
    }

    @Test
    void failsOnUnreadableJar() throws IOException {
        Path bad = Files.writeString(tmp.resolve("bad.jar"), "nope");

        assertThatThrownBy(() -> JavadocJar.open(bad)).isInstanceOf(JarReadException.class);
        assertThatThrownBy(() -> JavadocJar.open(tmp.resolve("missing.jar"))).isInstanceOf(JarReadException.class);
    }
}
