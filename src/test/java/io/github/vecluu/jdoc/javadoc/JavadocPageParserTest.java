package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavadocPageParserTest {

    private static final TypeName UTIL = TypeName.of("com.example.Util");

    private final JavadocPageParser parser = new JavadocPageParser();

    private static MemberDescriptor method(String name, ParameterInfo... parameters) {
        return MemberDescriptor.builder().declaringType(UTIL).name(name).kind(MemberKind.METHOD)
                .signature("void " + name + "()").parameters(List.of(parameters)).visibility(Visibility.PUBLIC)
                .build();
    }

    private static MemberDescriptor field(String name) {
        return MemberDescriptor.builder().declaringType(UTIL).name(name).kind(MemberKind.FIELD)
                .signature("int " + name).visibility(Visibility.PUBLIC).build();
    }

    private static ParameterInfo param(String name, String type, String erased) {
        return new ParameterInfo(name, type, erased);
    }

    private static final String MODERN = """
            <html><body><main>
            <section class="class-description" id="class-description">
            <dl class="notes"><dt>All Implemented Interfaces:</dt><dd><code>Runnable</code></dd></dl>
            <hr>
            <div class="type-signature"><span class="modifiers">public final class </span>Util</div>
            <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span>
            <div class="deprecation-comment">use <a href="Other.html"><code>Other</code></a> instead</div></div>
            <div class="block">Static helpers, e.g. for <a href="Shape.html"><code>shapes</code></a>.
            <p>Second paragraph with <code>code</code>.
            <pre><code>int x = 1;
              y();
            </code></pre></div>
            <dl class="notes">
            <dt>Type Parameters:</dt><dd><span id="type-param-T"><code>T</code> - the type</span></dd>
            <dt>Since:</dt><dd>1.0</dd>
            <dt>See Also:</dt><dd><ul class="tag-list"><li><a href="Shape.html#area()"><code>Shape.area()</code></a></li>
            <li><a href="Other.html"><code>Other</code></a></li></ul></dd>
            </dl>
            </section>
            <section class="details"><ul>
            <li><section class="detail" id="LIMIT"><h3>LIMIT</h3>
            <div class="block">The limit.</div>
            <dl class="notes"><dt>See Also:</dt><dd><ul class="tag-list"><li><a href="../constant-values.html#x">Constant Field Values</a></li></ul></dd></dl>
            </section></li>
            <li><section class="detail" id="max(T,T)"><h3 id="max(java.lang.Comparable,java.lang.Comparable)">max</h3>
            <div class="block">Returns the larger value. See <a href="#max(int,int)"><code>max(int, int)</code></a>.</div>
            <dl class="notes">
            <dt>Type Parameters:</dt><dd><span id="max(T,T)-type-param-T"><code>T</code> - the value type</span></dd>
            <dt>Parameters:</dt><dd><code>a</code> - the first</dd><dd><code>b</code> - the second</dd>
            <dt>Returns:</dt><dd>the larger, <code>a</code> if equal</dd>
            <dt>Throws:</dt><dd><code><a href="x/NullPointerException.html">NullPointerException</a></code> - if null</dd>
            <dd><code>IllegalStateException</code></dd>
            <dt>Specified by:</dt><dd><code>max</code> in interface <code>Maxer</code></dd>
            <dt>API Note:</dt><dd>Use with care.</dd>
            </dl></section></li>
            <li><section class="detail" id="max(int,int)"><h3>max</h3>
            <div class="block"><span class="description-from-type-label">Description copied from interface:&nbsp;<code><a href="Maxer.html#max(int,int)">Maxer</a></code></span></div>
            <div class="block">Copied doc.</div>
            </section></li>
            <li><section class="detail" id="old()"><h3>old</h3>
            <div class="deprecation-block"><span class="deprecated-label">Deprecated, for removal: This API element is subject to removal in a future version.</span>
            <div class="deprecation-comment">do not use</div></div>
            <div class="block">Old method.</div>
            </section></li>
            <li><section class="detail" id="markedOnly()"><h3>markedOnly</h3>
            <div class="deprecation-block"><span class="deprecated-label">Deprecated.</span></div>
            <div class="block">Only annotated.</div>
            </section></li>
            <li><section class="detail" id="undocumented()"><h3>undocumented</h3>
            <div class="member-signature">void undocumented()</div>
            </section></li>
            </ul></section>
            </main></body></html>
            """;

    @Test
    void rendersModernClassDescription() {
        RenderedDoc doc = parser.parseTypePage(MODERN, UTIL).typeDoc();

        assertThat(doc.description()).isEqualTo("""
                Static helpers, e.g. for shapes.

                Second paragraph with `code`.

                int x = 1;
                  y();""");
        assertThat(doc.firstSentence()).isEqualTo("Static helpers, e.g. for shapes.");
        assertThat(doc.blockTags()).containsExactly("@param <T> the type", "@see Shape#area()", "@see Other",
                "@since 1.0", "@deprecated use Other instead");
        assertThat(doc.inheritsDoc()).isFalse();
    }

    @Test
    void rendersModernMemberDetails() {
        JavadocTypePage page = parser.parseTypePage(MODERN, UTIL);
        RenderedDoc max = page.memberDoc(method("max", param("a", "T", "Comparable"), param("b", "T", "Comparable")))
                .orElseThrow();

        assertThat(max.description()).isEqualTo("Returns the larger value. See #max(int, int).");
        assertThat(max.blockTags()).containsExactly(
                "@apiNote Use with care.",
                "@param <T> the value type",
                "@param a the first",
                "@param b the second",
                "@return the larger, `a` if equal",
                "@throws NullPointerException if null",
                "@throws IllegalStateException");
    }

    @Test
    void usesCopiedDescriptionsAndSkipsTheirLabel() {
        RenderedDoc doc = parser.parseTypePage(MODERN, UTIL)
                .memberDoc(method("max", param("a", "int", "int"), param("b", "int", "int"))).orElseThrow();

        assertThat(doc.description()).isEqualTo("Copied doc.");
        assertThat(doc.inheritsDoc()).isFalse();
    }

    @Test
    void deprecationOnlyWithComment() {
        JavadocTypePage page = parser.parseTypePage(MODERN, UTIL);

        assertThat(page.memberDoc(method("old")).orElseThrow().blockTags()).containsExactly("@deprecated do not use");
        assertThat(page.memberDoc(method("markedOnly")).orElseThrow().blockTags()).isEmpty();
    }

    @Test
    void fieldsDropConstantValuesLinks() {
        RenderedDoc doc = parser.parseTypePage(MODERN, UTIL).memberDoc(field("LIMIT")).orElseThrow();

        assertThat(doc.description()).isEqualTo("The limit.");
        assertThat(doc.blockTags()).isEmpty();
    }

    @Test
    void undocumentedAndUnknownMembers() {
        JavadocTypePage page = parser.parseTypePage(MODERN, UTIL);

        assertThat(page.memberDoc(method("undocumented"))).contains(RenderedDoc.missing());
        assertThat(page.memberDoc(method("absent"))).isEmpty();
        assertThat(page.members()).hasSize(6);
        assertThat(page.members().get(1).anchors()).hasSize(2);
        assertThat(page.members().get(1).name()).isEqualTo("max");
    }

    @Test
    void fuzzyMatchesByNameAndArityOnlyWhenUnique() {
        JavadocTypePage page = parser.parseTypePage(MODERN, UTIL);

        assertThat(page.memberDoc(method("old", param("x", "Unknown", "Unknown")))).isEmpty();
        assertThat(page.memberDoc(method("max", param("a", "long", "long"), param("b", "long", "long")))).isEmpty();
        assertThat(page.memberDoc(method("max", param("a", "long", "long")))).isEmpty();
    }

    @Test
    void pageWithoutDescription() {
        JavadocTypePage page = parser.parseTypePage("<html><body><p>nothing</p></body></html>", UTIL);

        assertThat(page.typeDoc()).isEqualTo(RenderedDoc.missing());
        assertThat(page.members()).isEmpty();
    }

    private static final String LEGACY = """
            <html><body>
            <div class="header"><h2 title="Class Util" class="title">Class Util</h2></div>
            <div class="contentContainer">
            <div class="description"><ul class="blockList"><li class="blockList">
            <dl><dt>All Implemented Interfaces:</dt><dd>Runnable</dd></dl>
            <hr><br><pre>public class <span class="typeNameLabel">Util</span></pre>
            <div class="block">Legacy helpers.</div>
            <dl><dt><span class="simpleTagLabel">Since:</span></dt><dd>0.9</dd>
            <dt><span class="seeLabel">See Also:</span></dt><dd><a href="Shape.html#area--"><code>Shape.area()</code></a>, <a href="Other.html"><code>Other</code></a></dd></dl>
            </li></ul></div>
            <div class="details"><ul class="blockList"><li class="blockList">
            <ul class="blockList"><li class="blockList"><a name="method.detail"><!-- --></a><h3>Method Detail</h3>
            <a name="of-java.lang.Object:A-"><!-- --></a>
            <ul class="blockList"><li class="blockList"><h4>of</h4>
            <pre>public static&nbsp;Util&nbsp;of(Object...&nbsp;values)</pre>
            <div class="block">Creates one.</div>
            <dl><dt><span class="paramLabel">Parameters:</span></dt><dd><code>values</code> - the values</dd>
            <dt><span class="returnLabel">Returns:</span></dt><dd>a util</dd>
            <dt><span class="throwsLabel">Throws:</span></dt><dd><code>java.lang.IllegalArgumentException</code> - if empty</dd></dl>
            </li></ul>
            <a name="gone--"><!-- --></a>
            <ul class="blockListLast"><li class="blockList"><h4>gone</h4>
            <pre>public&nbsp;void&nbsp;gone()</pre>
            <div class="block"><span class="deprecatedLabel">Deprecated.</span>&nbsp;<span class="deprecationComment">use <a href="#of-java.lang.Object:A-"><code>of(Object...)</code></a></span></div>
            <div class="block">Gone soon.</div>
            </li></ul>
            </li></ul>
            </li></ul></div>
            </div></body></html>
            """;

    @Test
    void readsLegacyLayout() {
        JavadocTypePage page = parser.parseTypePage(LEGACY, UTIL);

        assertThat(page.typeDoc().description()).isEqualTo("Legacy helpers.");
        assertThat(page.typeDoc().blockTags()).containsExactly("@see Shape#area()", "@see Other", "@since 0.9");

        RenderedDoc of = page.memberDoc(method("of", param("values", "Object...", "Object[]"))).orElseThrow();
        assertThat(of.description()).isEqualTo("Creates one.");
        assertThat(of.blockTags()).containsExactly("@param values the values", "@return a util",
                "@throws java.lang.IllegalArgumentException if empty");

        RenderedDoc gone = page.memberDoc(method("gone")).orElseThrow();
        assertThat(gone.description()).isEqualTo("Gone soon.");
        assertThat(gone.blockTags()).containsExactly("@deprecated use #of(Object...)");
    }

    @Test
    void parsesPackagePages() {
        String modern = """
                <main><div class="header"><h1>Package a.b</h1></div>
                <section class="package-description" id="package-description">
                <div class="block">The a.b package.
                <p>More.</div>
                <dl class="notes"><dt>Since:</dt><dd>2.0</dd></dl></section>
                <section class="summary"><div class="block">Type summary.</div></section></main>
                """;
        String legacy = """
                <div class="contentContainer"><ul class="blockList"><li><table><tr><td>
                <div class="block">Summary row.</div></td></tr></table></li></ul>
                <a name="package.description"><!-- --></a><h2>Package a.b Description</h2>
                <div class="block">Legacy package doc.</div></div>
                """;

        RenderedDoc doc = parser.parsePackagePage(modern, "a.b");
        assertThat(doc.description()).isEqualTo("The a.b package.\n\nMore.");
        assertThat(doc.blockTags()).containsExactly("@since 2.0");
        assertThat(parser.parsePackagePage(legacy, "a.b").description()).isEqualTo("Legacy package doc.");
        assertThat(parser.parsePackagePage("<p>none</p>", "a.b")).isEqualTo(RenderedDoc.missing());
    }
}
