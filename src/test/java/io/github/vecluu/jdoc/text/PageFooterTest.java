package io.github.vecluu.jdoc.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageFooterTest {

    @Test
    void cappedPageFooterNamesTheNextCommand() {
        PageFooter footer = new PageFooter("jdoc class java.util.Optional");

        assertThat(footer.footer(1, 3)).isEqualTo("page 1/3 · next: jdoc class java.util.Optional --page 2");
        assertThat(footer.footer(2, 3)).isEqualTo("page 2/3 · next: jdoc class java.util.Optional --page 3");
    }

    @Test
    void lastPageFooterHasNoNextCommand() {
        assertThat(new PageFooter("jdoc class X").footer(3, 3)).isEqualTo("page 3/3");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "jdoc class X --page 2|jdoc class X",
        "jdoc class X --page=2|jdoc class X",
        "jdoc --page 2 class X|jdoc class X",
        "jdoc class X --page|jdoc class X",
        "jdoc class X --page   12 --from a/B.java|jdoc class X --from a/B.java",
        "jdoc class X --pages 2|jdoc class X --pages 2",
        "jdoc class my--page|jdoc class my--page",
        "  jdoc class X  |jdoc class X",
    })
    void removesThePageOption(String input, String expected) {
        assertThat(new PageFooter(input).commandLine()).isEqualTo(expected);
    }

    @Test
    void handlesMissingCommandLine() {
        assertThat(new PageFooter(null).commandLine()).isEmpty();
        assertThat(new PageFooter("").footer(1, 2)).isEqualTo("page 1/2 · next: --page 2");
    }

    @Test
    void maxFooterLengthCoversEveryPageOfTheResult() {
        PageFooter footer = new PageFooter("jdoc class X");
        for (int total = 1; total <= 120; total++) {
            int max = footer.maxFooterCharacters(total);
            for (int page = 1; page <= total; page++) {
                assertThat(footer.footer(page, total).length() + 1).isLessThanOrEqualTo(max);
            }
        }
    }
}
