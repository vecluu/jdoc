package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.TypeName;
import org.junit.jupiter.api.Test;

class JavadocLinkRewriterTest {

    private static final TypeName PAGE = TypeName.of("com.example.Shapes$Registry");

    private final JavadocLinkRewriter rewriter = new JavadocLinkRewriter();

    @Test
    void rewritesOtherPageMemberLinks() {
        assertThat(rewriter.rewritten("Shape.html#scale(double)", "Shape.scale(double)", PAGE))
                .contains("Shape#scale(double)");
        assertThat(rewriter.rewritten("../util/Map.Entry.html#getKey()", "Map.Entry.getKey()", PAGE))
                .contains("Map.Entry#getKey()");
        assertThat(rewriter.rewritten("https://docs.oracle.com/x/Integer.html#MIN_VALUE", "Integer.MIN_VALUE", PAGE))
                .contains("Integer#MIN_VALUE");
        assertThat(rewriter.rewritten("Circle.html#%3Cinit%3E(double)", "Circle(double)", PAGE))
                .contains("Circle#Circle(double)");
    }

    @Test
    void rewritesSamePageMemberLinksWithHash() {
        assertThat(rewriter.rewritten("#largest(com.example.Shape...)", "largest(Shape...)", PAGE))
                .contains("#largest(Shape...)");
        assertThat(rewriter.rewritten("#max(T,T)", "max(Comparable, Comparable)", PAGE))
                .contains("#max(Comparable, Comparable)");
        assertThat(rewriter.rewritten("#<init>()", "Registry()", PAGE)).contains("#Registry()");
    }

    @Test
    void keepsExplicitLabelsAndNonMemberLinks() {
        assertThat(rewriter.rewritten("#area()", "area", PAGE)).isEmpty();
        assertThat(rewriter.rewritten("Shape.html#name()", "the name", PAGE)).isEmpty();
        assertThat(rewriter.rewritten("Shape.html", "Shape", PAGE)).isEmpty();
        assertThat(rewriter.rewritten("#param-x", "x", PAGE)).isEmpty();
        assertThat(rewriter.rewritten("#ORIGIN", "the origin", PAGE)).isEmpty();
        assertThat(rewriter.rewritten("#area()", "", PAGE)).isEmpty();
    }
}
