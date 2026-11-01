package io.github.vecluu.jdoc.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocLinesTest {

    @Test
    void descriptionBlankLineThenTags() {
        assertThat(DocLines.of(new RenderedDoc("First.\n\nSecond.  ", "First.", List.of("@return r"), false)))
                .containsExactly("First.", "", "Second.", "", "@return r");
    }

    @Test
    void tagsOnlyOrNothing() {
        assertThat(DocLines.of(new RenderedDoc("", "", List.of("@since 1"), false))).containsExactly("@since 1");
        assertThat(DocLines.of(RenderedDoc.missing())).isEmpty();
    }

    @Test
    void verbatimKeepsIndentationAndDropsOuterBlankLines() {
        assertThat(DocLines.verbatim("\n  a() {\n      b;\n  }  \n\n")).containsExactly("  a() {", "      b;", "  }");
    }
}
