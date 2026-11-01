package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTextFormatterTest {

    private final HtmlTextFormatter formatter = new HtmlTextFormatter();

    @Test
    void emptyInput() {
        assertThat(formatter.format(null)).isEmpty();
        assertThat(formatter.format("  \n ")).isEmpty();
    }

    @Test
    void collapsesWhitespace() {
        assertThat(formatter.format("  Returns   the\n   value\tnow.  ")).isEqualTo("Returns the value now.");
    }

    @Test
    void codeAndTtBecomeBackticks() {
        assertThat(formatter.format("Returns <code>true</code> if <tt>x</tt> is <code>null</code>."))
                .isEqualTo("Returns `true` if `x` is `null`.");
        assertThat(formatter.format("a<code></code>b")).isEqualTo("ab");
        assertThat(formatter.format("call <code>foo</code>, then")).isEqualTo("call `foo`, then");
    }

    @Test
    void linksBecomeTheirPlainText() {
        assertThat(formatter.format("See <a href=\"Foo.html#bar(int)\"><code>Foo.bar(int)</code></a> for details."))
                .isEqualTo("See Foo.bar(int) for details.");
        assertThat(formatter.format("<a href=\"x\">the  docs</a>")).isEqualTo("the docs");
    }

    @Test
    void paragraphsBecomeBlankLines() {
        assertThat(formatter.format("First.<p>Second\npart.<p>Third.</p>Fourth."))
                .isEqualTo("First.\n\nSecond part.\n\nThird.\n\nFourth.");
        assertThat(formatter.format("<p>Lead.</p>")).isEqualTo("Lead.");
        assertThat(formatter.format("One.<p></p><p></p>Two.")).isEqualTo("One.\n\nTwo.");
    }

    @Test
    void preIsKeptVerbatimWithoutTags() {
        String html = "Example:\n<pre>\n   Foo f = new Foo();\n   if (a &lt; b) {\n       f.<b>run</b>();\n   }\n</pre>Done.";
        assertThat(formatter.format(html)).isEqualTo(
                "Example:\n\nFoo f = new Foo();\nif (a < b) {\n    f.run();\n}\n\nDone.");
    }

    @Test
    void preWithCodeFromJavadocHtml() {
        String html = "A circle defined by its radius.\n\n<pre><code>Circle c = new Circle(2.0);\n"
                + "double a = c.area();\n</code></pre>";
        assertThat(formatter.format(html))
                .isEqualTo("A circle defined by its radius.\n\nCircle c = new Circle(2.0);\ndouble a = c.area();");
    }

    @Test
    void preKeepsRelativeIndentationAndBlankLines() {
        assertThat(formatter.format("<pre>\n  a\n\n    b\n</pre>")).isEqualTo("a\n\n  b");
    }

    @Test
    void listsBecomeDashLines() {
        String html = "Highlights:\n<ul>\n  <li>one <code>a</code></li>\n  <li>two\n    <ul><li>nested</li></ul>\n"
                + "  </li>\n</ul>\nAfter.";
        assertThat(formatter.format(html)).isEqualTo("Highlights:\n- one `a`\n- two\n  - nested\nAfter.");
        assertThat(formatter.format("<ol><li>x</li><li>y</li></ol>")).isEqualTo("- x\n- y");
    }

    @Test
    void definitionListsAndBreaks() {
        assertThat(formatter.format("<dl><dt>Since:</dt><dd>1.0</dd></dl>")).isEqualTo("Since:\n  1.0");
        assertThat(formatter.format("a<br>b<br/>c")).isEqualTo("a\nb\nc");
    }

    @Test
    void otherTagsAreStrippedAndEntitiesDecoded() {
        assertThat(formatter.format("<b>Bold</b> and <i>it</i>&nbsp;&amp; <span class=\"x\">span</span> &lt;T&gt; &#64;"))
                .isEqualTo("Bold and it & span <T> @");
        assertThat(formatter.format("<em>a</em><strong>b</strong>")).isEqualTo("ab");
        assertThat(formatter.format("x<script>alert(1)</script>y<style>p{}</style>")).isEqualTo("xy");
    }

    @Test
    void headingsAndTables() {
        assertThat(formatter.format("<h2>Title</h2>Body")).isEqualTo("Title\n\nBody");
        assertThat(formatter.format("<table><tr><th>K</th><th>V</th></tr><tr><td>a</td><td>1</td></tr></table>"))
                .isEqualTo("K V\na 1");
    }

    @Test
    void javadocMemberBlock() {
        String html = "<div class=\"block\">Returns the <a href=\"#x\"><code>Optional</code></a> value,\n"
                + " e.g. <code>Optional.of(1)</code>.</div>";
        assertThat(formatter.format(html)).isEqualTo("Returns the Optional value, e.g. `Optional.of(1)`.");
    }
}
