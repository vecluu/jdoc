package io.github.vecluu.jdoc.javadoc;

import static io.github.vecluu.jdoc.javadoc.DocCommentFixture.comment;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.RenderedDoc;
import org.junit.jupiter.api.Test;

class DocCommentRendererTest {

    private final DocCommentRenderer renderer = new DocCommentRenderer();

    private RenderedDoc render(String... lines) {
        return renderer.render(comment(lines));
    }

    @Test
    void missingCommentInheritsDoc() {
        assertThat(renderer.render(null)).isEqualTo(RenderedDoc.missing());
        assertThat(renderer.render(DocCommentFixture.parse("class X {}"))).isEqualTo(RenderedDoc.missing());
    }

    @Test
    void plainDescription() {
        RenderedDoc doc = render("Returns the  value.", "More text here.");
        assertThat(doc.description()).isEqualTo("Returns the value. More text here.");
        assertThat(doc.firstSentence()).isEqualTo("Returns the value.");
        assertThat(doc.blockTags()).isEmpty();
        assertThat(doc.inheritsDoc()).isFalse();
    }

    @Test
    void codeTagAndCodeElementBecomeBackticks() {
        assertThat(render("Use {@code List<String>} or <code>x</code>.").description())
                .isEqualTo("Use `List<String>` or `x`.");
    }

    @Test
    void literalIsPlainEscapedText() {
        assertThat(render("Compares {@literal a < b && c}.").description()).isEqualTo("Compares a < b && c.");
    }

    @Test
    void linksRenderTheReferenceAsWritten() {
        assertThat(render("See {@link Foo#bar(int)} and {@linkplain java.util.List lists}.").description())
                .isEqualTo("See Foo#bar(int) and lists.");
        assertThat(render("See {@link #radius()} and {@link Shapes#largest(Shape...) largest}.").description())
                .isEqualTo("See #radius() and largest.");
    }

    @Test
    void labelIsPlainTextLikeAllLinks() {
        assertThat(render("See {@link Foo the {@code Foo} type}.").description()).isEqualTo("See the Foo type.");
    }

    @Test
    void valueRendersItsReference() {
        assertThat(render("Default is {@value Foo#MAX}.").description()).isEqualTo("Default is Foo#MAX.");
        assertThat(render("Value {@value}.").description()).isEqualTo("Value .");
    }

    @Test
    void paragraphsBecomeBlankLines() {
        assertThat(render("First.", "<p>Second.", "<p>", "Third.").description())
                .isEqualTo("First.\n\nSecond.\n\nThird.");
    }

    @Test
    void preBlocksAreVerbatimIncludingCode() {
        RenderedDoc doc = render("Example:", "<pre>{@code", "List<String> l = new ArrayList<>();",
                "if (l.isEmpty()) {", "    l.add(\"x\");", "}", "}</pre>", "Done.");
        assertThat(doc.description()).isEqualTo("""
                Example:

                List<String> l = new ArrayList<>();
                if (l.isEmpty()) {
                    l.add("x");
                }

                Done.""");
        assertThat(doc.firstSentence()).isEqualTo("Example:");
    }

    @Test
    void snippetBecomesPreBlock() {
        RenderedDoc doc = render("Example:", "{@snippet :", "  var x = 1;", "}");
        assertThat(doc.description()).isEqualTo("Example:\n\nvar x = 1;");
    }

    @Test
    void otherHtmlIsStrippedAndEntitiesDecoded() {
        assertThat(render("A <b>bold</b> &amp; <i>nice</i> &lt;tag&gt;.").description())
                .isEqualTo("A bold & nice <tag>.");
    }

    @Test
    void listsUseSharedRules() {
        assertThat(render("Items:", "<ul>", "<li>one</li>", "<li>{@code two}</li>", "</ul>").description())
                .isEqualTo("Items:\n- one\n- `two`");
    }

    @Test
    void blockTagsOneLineEach() {
        RenderedDoc doc = render("Does it.", "", "@param <T> the type", "@param value the value,",
                "       continued", "@return the {@code result}", "@throws IllegalStateException if bad",
                "@exception java.io.IOException on I/O", "@since 1.2", "@deprecated use {@link #other()}");
        assertThat(doc.blockTags()).containsExactly(
                "@param <T> the type",
                "@param value the value, continued",
                "@return the `result`",
                "@throws IllegalStateException if bad",
                "@throws java.io.IOException on I/O",
                "@since 1.2",
                "@deprecated use #other()");
        assertThat(renderer.hasDeprecatedTag(comment("x", "@deprecated gone"))).isTrue();
        assertThat(renderer.hasDeprecatedTag(comment("x", "@since 1"))).isFalse();
        assertThat(renderer.hasDeprecatedTag(null)).isFalse();
    }

    @Test
    void seeAndNoteTags() {
        RenderedDoc doc = render("Does it.", "@see Foo#bar(int)", "@see \"The Book\"",
                "@see <a href=\"https://x\">site</a>", "@apiNote use <em>carefully</em>", "@implNote fast",
                "@implSpec calls {@code foo}", "@author nobody", "@jls 8.4 Method Declarations");
        assertThat(doc.blockTags()).containsExactly(
                "@see Foo#bar(int)", "@see \"The Book\"", "@see site", "@apiNote use carefully", "@implNote fast",
                "@implSpec calls `foo`");
    }

    @Test
    void inheritDocSetsFlagAndKeepsOtherText() {
        RenderedDoc doc = render("{@inheritDoc}", "<p>Extra text.");
        assertThat(doc.inheritsDoc()).isTrue();
        assertThat(doc.description()).isEqualTo("Extra text.");
        assertThat(render("Text.", "@return {@inheritDoc}").inheritsDoc()).isTrue();
    }

    @Test
    void firstSentenceSkipsAbbreviations() {
        assertThat(render("Uses e.g. a map, i.e. a table. Second.").firstSentence())
                .isEqualTo("Uses e.g. a map, i.e. a table.");
        assertThat(render("Calls {@code a.b()} first. Then.").firstSentence()).isEqualTo("Calls `a.b()` first.");
    }

    @Test
    void summaryTagDefinesFirstSentence() {
        RenderedDoc doc = render("{@summary Does a thing. Really well.} More text.");
        assertThat(doc.firstSentence()).isEqualTo("Does a thing. Really well.");
        assertThat(doc.description()).isEqualTo("Does a thing. Really well. More text.");
    }

    @Test
    void inlineReturnAddsDescriptionAndReturnTag() {
        RenderedDoc doc = render("{@return the size of the list} Never negative.");
        assertThat(doc.description()).isEqualTo("Returns the size of the list. Never negative.");
        assertThat(doc.firstSentence()).isEqualTo("Returns the size of the list.");
        assertThat(doc.blockTags()).containsExactly("@return the size of the list");
        assertThat(render("{@return the size}", "@return the count").blockTags()).containsExactly("@return the count");
    }

    @Test
    void otherInlineTags() {
        assertThat(render("Set {@systemProperty user.dir} {@index term the term} here{@docRoot}.").description())
                .isEqualTo("Set `user.dir` term here.");
    }

    @Test
    void parameterDescriptionsForRecords() {
        assertThat(renderer.parameterDescriptions(comment("A point.", "@param <T> type", "@param x the {@code x}",
                "@param y the y"))).containsExactly(
                java.util.Map.entry("x", "the `x`"), java.util.Map.entry("y", "the y"));
        assertThat(renderer.parameterDescriptions(null)).isEmpty();
    }

    @Test
    void emptyCommentIsEmptyButNotInheriting() {
        RenderedDoc doc = render("");
        assertThat(doc.isEmpty()).isTrue();
        assertThat(doc.inheritsDoc()).isFalse();
    }
}
