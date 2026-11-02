package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JavadocJarReaderTest {

    /** In-memory page source. */
    private static final class FakePages implements JavadocPageSource {
        private final Map<String, String> pages = new HashMap<>();

        FakePages with(String path, String html) {
            pages.put(path, html);
            return this;
        }

        @Override
        public Optional<String> page(String entryPath) {
            return Optional.ofNullable(pages.get(entryPath));
        }

        @Override
        public boolean hasPage(String entryPath) {
            return pages.containsKey(entryPath);
        }
    }

    private static String typePage(String description, String since) {
        return "<section class=\"class-description\"><div class=\"block\">" + description + "</div>"
                + "<dl class=\"notes\"><dt>Since:</dt><dd>" + since + "</dd></dl></section>";
    }

    @Test
    void locatesPages() {
        assertThat(JavadocJarReader.typePagePath(TypeName.of("a.b.Outer$Inner"))).isEqualTo("a/b/Outer.Inner.html");
        assertThat(JavadocJarReader.packagePagePath("a.b")).isEqualTo("a/b/package-summary.html");
        assertThat(JavadocJarReader.packagePagePath("")).isEqualTo("package-summary.html");
    }

    @Test
    void readsTypePagesAndRemovesSinceCopiedFromEnclosingType() {
        FakePages pages = new FakePages()
                .with("a/Outer.html", typePage("Outer.", "1.0"))
                .with("a/Outer.Mid.html", typePage("Mid.", "1.0"))
                .with("a/Outer.Mid.Inner.html", typePage("Inner.", "1.0"))
                .with("a/Outer.Other.html", typePage("Other.", "2.0"));
        JavadocJarReader reader = new JavadocJarReader(pages, new JavadocPageParser());

        assertThat(reader.typePage(TypeName.of("a.Missing"))).isEmpty();
        assertThat(reader.typePage(TypeName.of("a.Outer")).orElseThrow().typeDoc().blockTags())
                .containsExactly("@since 1.0");
        assertThat(reader.typePage(TypeName.of("a.Outer$Mid")).orElseThrow().typeDoc().blockTags()).isEmpty();
        assertThat(reader.typePage(TypeName.of("a.Outer$Mid$Inner")).orElseThrow().typeDoc().blockTags()).isEmpty();
        assertThat(reader.typePage(TypeName.of("a.Outer$Other")).orElseThrow().typeDoc().blockTags())
                .containsExactly("@since 2.0");
    }

    @Test
    void readsPackageDoc() {
        FakePages pages = new FakePages()
                .with("a/package-summary.html",
                        "<section class=\"package-description\"><div class=\"block\">Pkg a.</div></section>")
                .with("b/package-summary.html", "<main>no description</main>");
        JavadocJarReader reader = new JavadocJarReader(pages, new JavadocPageParser());

        assertThat(reader.packageDoc("a")).map(doc -> doc.description()).contains("Pkg a.");
        assertThat(reader.packageDoc("b")).isEmpty();
        assertThat(reader.packageDoc("c")).isEmpty();
    }
}
