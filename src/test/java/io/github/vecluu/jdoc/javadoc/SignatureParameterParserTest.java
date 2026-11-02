package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

class SignatureParameterParserTest {

    private final SignatureParameterParser parser = new SignatureParameterParser();

    private static JavadocParameter p(String type, String name) {
        return new JavadocParameter(type, name);
    }

    private static Element section(String html) {
        return Jsoup.parse(html).body().child(0);
    }

    @Test
    void readsTheModernLayoutWithLinksGenericsAndVarargs() {
        Element section = section("""
                <section class="detail" id="byName(java.util.Map,java.lang.String...)"><h3>byName</h3>
                <div class="member-signature"><span class="modifiers">public static</span>&nbsp;<span class="type-parameters">&lt;K extends <a href="Shape.html">Shape</a>&gt;</span>&nbsp;<span class="return-type"><a href="x/Map.html">Map</a>&lt;K, <a href="x/List.html">List</a>&lt;? extends K&gt;&gt;</span>&nbsp;<span class="element-name">byName</span><wbr><span class="parameters">(<a href="x/Map.html">Map</a>&lt;<a href="x/String.html">String</a>, ? super K&gt;&nbsp;index,
                 <a href="x/String.html">String</a>...&nbsp;names)</span>
                               throws <span class="exceptions"><a href="x/IOException.html">IOException</a></span></div>
                <div class="block">Doc with (parentheses) <pre>x(1)</pre></div>
                </section>""");

        assertThat(parser.parameters(section)).contains(List.of(
                p("Map<String, ? super K>", "index"), p("String...", "names")));
    }

    @Test
    void readsTheJdk11LayoutWithZeroWidthSpaceAndAnnotations() {
        Element section = section("""
                <li class="blockList"><h4>put</h4>
                <pre class="methodSignature">@Deprecated(since="2.0")
                public&nbsp;<a href="Box.html">Box</a>&lt;<a href="Box.html">T</a>&gt;&nbsp;put&#8203;(@Nonnull <a href="Box.html">T</a>&nbsp;item,
                                  int[][]&nbsp;counts,
                                  java.lang.@Nullable String&nbsp;label)</pre>
                </li>""");

        assertThat(parser.parameters(section)).contains(List.of(
                p("T", "item"), p("int[][]", "counts"), p("java.lang.String", "label")));
    }

    @Test
    void readsTheJdk8LayoutWithNestedTypesAndMultipleLines() {
        Element section = section("""
                <li class="blockList"><h4>join</h4>
                <pre>public static&nbsp;&lt;K,V&gt;&nbsp;<a href="x/String.html" title="class in java.lang">String</a>&nbsp;join(<a href="x/Map.Entry.html">Map.Entry</a>&lt;K,V&gt;[]&nbsp;entries,
                                  char&nbsp;separator)
                           throws <a href="x/IOException.html">IOException</a></pre>
                <div class="block">Joins. <pre>join(a, b)</pre></div>
                </li>""");

        assertThat(parser.parameters(section)).contains(List.of(
                p("Map.Entry<K,V>[]", "entries"), p("char", "separator")));
    }

    @Test
    void emptyParameterListsAndReceiverParameters() {
        assertThat(parser.parse("public void run()")).contains(List.of());
        assertThat(parser.parse("public void run(Outer this, int times)")).contains(List.of(p("int", "times")));
        assertThat(parser.parse("public Outer(final int size)")).contains(List.of(p("int", "size")));
    }

    @Test
    void unknownWhenTheSignatureCannotBeRead() {
        assertThat(parser.parse("public static final int LIMIT")).isEmpty();
        assertThat(parser.parse("void broken(int")).isEmpty();
        assertThat(parser.parse("void typeOnly(int, String)")).isEmpty();
        assertThat(parser.parse("void bad(int 1x)")).isEmpty();
        assertThat(parser.parameters(section("<section class=\"detail\"><h3>x</h3></section>"))).isEmpty();
    }

    @Test
    void ignoresParenthesesInAnnotationsBeforeTheMember() {
        assertThat(parser.parse("@Retention(value=RUNTIME) @Foo(a = \"(\") public void go(long millis)"))
                .contains(List.of(p("long", "millis")));
    }
}
