package io.github.vecluu.jdoc.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocInheritanceTest {

    private static final RenderedDoc SUPER = new RenderedDoc("Returns the name. More text.", "Returns the name.",
            List.of("@param x the x", "@return the name", "@throws IllegalStateException if closed"), false);

    @Test
    void missingDocTakesTheInheritedDocWhole() {
        assertThat(DocInheritance.merge(RenderedDoc.missing(), SUPER)).isEqualTo(SUPER);
        assertThat(DocInheritance.needsInheritance(RenderedDoc.missing())).isTrue();
        assertThat(DocInheritance.needsInheritance(SUPER)).isFalse();
    }

    @Test
    void inheritDocPutsTheInheritedDescriptionFirstAndFillsMissingTags() {
        RenderedDoc own = new RenderedDoc("This implementation is fast.", "This implementation is fast.",
                List.of("@return a name", "@since 2.0"), true);

        RenderedDoc merged = DocInheritance.merge(own, SUPER);

        assertThat(merged.description()).isEqualTo("Returns the name. More text.\n\nThis implementation is fast.");
        assertThat(merged.firstSentence()).isEqualTo("Returns the name.");
        assertThat(merged.blockTags())
                .containsExactly("@param x the x", "@return a name", "@throws IllegalStateException if closed",
                        "@since 2.0");
        assertThat(merged.inheritsDoc()).isFalse();
    }

    @Test
    void emptyDescriptionWithOwnTagsTakesTheInheritedDescription() {
        RenderedDoc own = new RenderedDoc("", "", List.of("@return the own name"), false);

        RenderedDoc merged = DocInheritance.merge(own, SUPER);

        assertThat(merged.description()).isEqualTo(SUPER.description());
        assertThat(merged.blockTags()).containsExactly("@param x the x", "@return the own name",
                "@throws IllegalStateException if closed");
        assertThat(DocInheritance.needsInheritance(own)).isTrue();
    }

    @Test
    void ownTagOrderIsKeptWhenNothingIsAdded() {
        RenderedDoc own = new RenderedDoc("{@inheritDoc} done", "x",
                List.of("@since 1", "@return r", "@param x p", "@throws IllegalStateException never"), true);

        assertThat(DocInheritance.merge(own, SUPER).blockTags()).isEqualTo(own.blockTags());
    }

    @Test
    void stillUnresolvedInheritedDocKeepsTheFlag() {
        RenderedDoc partial = new RenderedDoc("Extra.", "Extra.", List.of(), true);

        assertThat(DocInheritance.merge(RenderedDoc.missing(), partial).inheritsDoc()).isTrue();
    }
}
