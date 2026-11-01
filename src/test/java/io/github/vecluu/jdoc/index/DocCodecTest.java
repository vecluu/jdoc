package io.github.vecluu.jdoc.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.error.IndexException;
import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocCodecTest {

    private static RenderedDoc roundTrip(RenderedDoc doc) {
        return DocCodec.decode(DocCodec.encode(doc));
    }

    @Test
    void roundTripsMissingAndEmptyDocs() {
        assertThat(roundTrip(RenderedDoc.missing())).isEqualTo(RenderedDoc.missing());
        RenderedDoc empty = new RenderedDoc("", "", List.of(), false);
        assertThat(roundTrip(empty)).isEqualTo(empty);
        assertThat(DocCodec.encode(empty)).hasSizeLessThan(6);
    }

    @Test
    void roundTripsShortDocsWithTagsAndAFirstSentenceThatIsAPrefix() {
        RenderedDoc doc = new RenderedDoc("Returns the value. More text.", "Returns the value.",
                List.of("@param x the x", "@return the value", "@throws IllegalStateException if absent"), true);

        byte[] encoded = DocCodec.encode(doc);

        assertThat(DocCodec.decode(encoded)).isEqualTo(doc);
        assertThat(encoded.length).isLessThan(doc.description().length() + 100);
    }

    @Test
    void roundTripsAFirstSentenceThatIsNotAPrefix() {
        RenderedDoc doc = new RenderedDoc("Returns\nthe value.  More.", "Returns the value.", List.of(), false);

        assertThat(roundTrip(doc)).isEqualTo(doc);
    }

    @Test
    void roundTripsUnicodeAndPreBlocks() {
        String description = """
                Größe — 大小 — 😀 and `code`.

                    int x = 1;  // <pre> stays verbatim
                      if (x > 0) { return "ä"; }

                Last paragraph.""";
        RenderedDoc doc = new RenderedDoc(description, "Größe — 大小 — 😀 and `code`.",
                List.of("@param ü unicode name", "@see Ünïcödé#m()"), false);

        assertThat(roundTrip(doc)).isEqualTo(doc);
    }

    @Test
    void deflatesLongDocsAndRoundTripsThem() {
        String paragraph = "This is a long paragraph about collections, maps and streams. ".repeat(40);
        String description = (paragraph + "\n\n").repeat(30).strip();
        List<String> tags = java.util.stream.IntStream.range(0, 200).mapToObj(i -> "@param p" + i + " text " + i)
                .toList();
        RenderedDoc doc = new RenderedDoc(description, "This is a long paragraph about collections, maps and "
                + "streams.", tags, false);

        byte[] encoded = DocCodec.encode(doc);

        assertThat(description.length()).isGreaterThan(70_000);
        assertThat(encoded.length).isLessThan(description.length() / 10);
        assertThat(DocCodec.decode(encoded)).isEqualTo(doc);
    }

    @Test
    void keepsIncompressibleLongTextPlain() {
        StringBuilder noise = new StringBuilder();
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < 400; i++) {
            noise.appendCodePoint(0x4e00 + random.nextInt(20_000));
        }
        RenderedDoc doc = new RenderedDoc(noise.toString(), "", List.of(), false);

        assertThat(roundTrip(doc)).isEqualTo(doc);
    }

    @Test
    void rejectsCorruptBytes() {
        byte[] valid = DocCodec.encode(new RenderedDoc("Some text. ".repeat(50), "Some text.", List.of(), false));

        assertThatThrownBy(() -> DocCodec.decode(new byte[0])).isInstanceOf(IndexException.class);
        assertThatThrownBy(() -> DocCodec.decode(new byte[] {(byte) 0x80})).isInstanceOf(IndexException.class);
        assertThatThrownBy(() -> DocCodec.decode(Arrays.copyOf(valid, valid.length / 2)))
                .isInstanceOf(IndexException.class);
        assertThatThrownBy(() -> DocCodec.decode(new byte[] {0, 5, 'a'})).isInstanceOf(IndexException.class);
    }
}
