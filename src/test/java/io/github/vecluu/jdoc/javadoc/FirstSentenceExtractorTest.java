package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FirstSentenceExtractorTest {

    private final FirstSentenceExtractor extractor = new FirstSentenceExtractor();

    @Test
    void emptyInput() {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extractor.extract(" \n")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Returns the value. More text.|Returns the value.",
            "Returns the value.|Returns the value.",
            "No period at all|No period at all",
            "Version 1.5 is used. Next.|Version 1.5 is used.",
            "Uses defaults, e.g. zero. Next.|Uses defaults, e.g. zero.",
            "Uses defaults (i.e. zero) here. Next.|Uses defaults (i.e. zero) here.",
            "Compares a vs. b. Next.|Compares a vs. b.",
            "Calls `a. b` first. Next.|Calls `a. b` first.",
            "Ends with etc. Next.|Ends with etc.",
            "Ends with period.\tTab next.|Ends with period.",
            "Is it? Yes. Next.|Is it? Yes."
    })
    void sentenceRules(String text, String expected) {
        assertThat(extractor.extract(text)).isEqualTo(expected);
    }

    @Test
    void stopsAtParagraphEnd() {
        assertThat(extractor.extract("First paragraph without period\n\nSecond. Third."))
                .isEqualTo("First paragraph without period");
    }

    @Test
    void joinsWrappedLines() {
        assertThat(extractor.extract("A sentence that\nwraps across\nlines. Next.")).isEqualTo(
                "A sentence that wraps across lines.");
    }

    @Test
    void stopsBeforeListsAndPreBlocks() {
        assertThat(extractor.extract("Options:\n- one\n- two")).isEqualTo("Options:");
        assertThat(extractor.extract("Example\n  code();")).isEqualTo("Example");
    }

    @Test
    void abbreviationNeedsWordBoundary() {
        assertThat(extractor.extract("Nice.g. Next.")).isEqualTo("Nice.g.");
    }
}
