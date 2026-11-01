package io.github.vecluu.jdoc.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PaginatorTest {

    private static final String HEADER = "a.B  public class  (lib 1.0)";
    private static final String COMMAND = "jdoc class a.B";
    /** Longest footer for results up to 9 pages, with its line break. */
    private static final int FOOTER = ("page 8/9 · next: " + COMMAND + " --page 9").length() + 1;

    @Test
    void defaultsToTwoThousandTokens() {
        assertThat(new Paginator().budget()).isEqualTo(TokenBudget.defaultBudget());
    }

    @Test
    void resultThatFitsExactlyIsOnePageWithoutFooter() {
        Paginator paginator = new Paginator(new TokenBudget(25));
        int body = 100 - (HEADER.length() + 1);
        TextResult result = TextResult.ofLines(HEADER, List.of("x".repeat(body - 1)));

        List<Page> pages = paginator.paginate(result, COMMAND);

        assertThat(pages).hasSize(1);
        Page page = pages.getFirst();
        assertThat(page.lines()).containsExactly(HEADER, "x".repeat(body - 1));
        assertThat(page.printedCharacters()).isEqualTo(100);
        assertThat(page.number()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void oneCharacterOverTheBudgetSplitsIntoPagesWithFooters() {
        Paginator paginator = new Paginator(new TokenBudget(50));
        int body = 200 - (HEADER.length() + 1);
        String first = "x".repeat(body / 2 - 1);
        String second = "y".repeat(body - first.length() - 2 + 1);
        TextResult result = TextResult.ofLines(HEADER, List.of(first, second));

        List<Page> pages = paginator.paginate(result, COMMAND);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).lines()).containsExactly(HEADER, first, "page 1/2 · next: jdoc class a.B --page 2");
        assertThat(pages.get(1).lines()).containsExactly(HEADER, second, "page 2/2");
        assertThat(pages.get(0).hasNext()).isTrue();
    }

    @Test
    void everyPageRepeatsTheHeaderAndStaysWithinBudget() {
        Paginator paginator = new Paginator(new TokenBudget(50));
        List<String> lines = IntStream.range(0, 60).mapToObj(i -> "line number " + i).toList();
        TextResult result = TextResult.ofLines(HEADER, lines);

        List<Page> pages = paginator.paginate(result, COMMAND + " --page 4");

        assertThat(pages).hasSizeGreaterThan(3);
        for (Page page : pages) {
            assertThat(page.lines().getFirst()).isEqualTo(HEADER);
            assertThat(page.printedCharacters()).isLessThanOrEqualTo(200);
            assertThat(page.total()).isEqualTo(pages.size());
        }
        assertThat(pages.getFirst().lines().getLast())
                .isEqualTo("page 1/" + pages.size() + " · next: jdoc class a.B --page 2");
        assertThat(pages.getLast().lines().getLast()).isEqualTo("page " + pages.size() + "/" + pages.size());
        assertThat(bodies(pages)).isEqualTo(lines);
    }

    @Test
    void blocksAreNotSplitWhenTheyFitOnAPage() {
        int capacity = 200 - (HEADER.length() + 1) - FOOTER;
        String line = "z".repeat(capacity / 3 - 1);
        TextResult result = TextResult.builder(HEADER)
                .line(line)
                .line(line)
                .block(List.of(line, line))
                .build();

        List<Page> pages = new Paginator(new TokenBudget(50)).paginate(result, COMMAND);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).lines()).hasSize(4);
        assertThat(pages.get(1).lines()).containsExactly(HEADER, line, line, "page 2/2");
    }

    @Test
    void blockLargerThanAPageIsSplitByLines() {
        int capacity = 200 - (HEADER.length() + 1) - FOOTER;
        String line = "w".repeat(capacity / 3 - 1);
        List<String> big = List.of("a" + line, "b" + line, "c" + line, "d" + line, "e" + line);
        TextResult result = TextResult.builder(HEADER).line("intro").block(big).build();

        List<Page> pages = new Paginator(new TokenBudget(50)).paginate(result, COMMAND);

        assertThat(pages).hasSize(3);
        assertThat(pages.getFirst().lines()).contains("intro", "a" + line);
        assertThat(bodies(pages)).containsExactlyElementsOf(concat(List.of("intro"), big));
        pages.forEach(page -> assertThat(page.printedCharacters()).isLessThanOrEqualTo(200));
    }

    @Test
    void lineLongerThanAPageGetsAPageOfItsOwn() {
        String huge = "h".repeat(500);
        TextResult result = TextResult.ofLines(HEADER, List.of("before", huge, "after"));

        List<Page> pages = new Paginator(new TokenBudget(50)).paginate(result, COMMAND);

        assertThat(pages).hasSize(3);
        assertThat(pages.get(1).lines()).containsExactly(HEADER, huge, "page 2/3 · next: jdoc class a.B --page 3");
        assertThat(bodies(pages)).containsExactly("before", huge, "after");
    }

    @Test
    void emptyBodyIsOnePage() {
        List<Page> pages = new Paginator(new TokenBudget(1)).paginate(new TextResult("a long header line", null), "");

        assertThat(pages).singleElement().satisfies(page -> assertThat(page.lines()).containsExactly(
                "a long header line"));
    }

    @Test
    void reservesRoomForMultiDigitPageNumbers() {
        List<String> lines = IntStream.range(0, 600).mapToObj(i -> "entry " + i).toList();
        TextResult result = TextResult.ofLines(HEADER, lines);

        List<Page> pages = new Paginator(new TokenBudget(30)).paginate(result, COMMAND);

        assertThat(pages.size()).isGreaterThan(99);
        pages.forEach(page -> assertThat(page.printedCharacters()).isLessThanOrEqualTo(120));
        assertThat(bodies(pages)).isEqualTo(lines);
        assertThat(pages.get(9).lines().getLast())
                .isEqualTo("page 10/" + pages.size() + " · next: jdoc class a.B --page 11");
    }

    @Test
    void pageReturnsTheRequestedPage() {
        TextResult result = TextResult.ofLines(HEADER,
                IntStream.range(0, 30).mapToObj(i -> "line " + i).toList());
        Paginator paginator = new Paginator(new TokenBudget(40));
        List<Page> pages = paginator.paginate(result, COMMAND);

        PageOutcome outcome = paginator.page(result, 2, COMMAND + " --page 2");

        assertThat(outcome).isEqualTo(new PageOutcome.Shown(pages.get(1)));
    }

    @Test
    void pageOutOfRangeIsReportedAsAValue() {
        TextResult result = TextResult.ofLines(HEADER, List.of("short"));
        Paginator paginator = new Paginator();

        assertThat(paginator.page(result, 1, COMMAND)).isInstanceOf(PageOutcome.Shown.class);
        assertThat(paginator.page(result, 2, COMMAND))
                .isEqualTo(new PageOutcome.OutOfRange(2, 1));
        assertThat(paginator.page(result, 0, COMMAND)).isInstanceOf(PageOutcome.OutOfRange.class);
        assertThat(paginator.page(result, -1, COMMAND)).isInstanceOf(PageOutcome.OutOfRange.class);
        assertThat(new PageOutcome.OutOfRange(2, 1).message())
                .isEqualTo("page 2 does not exist: this result has 1 page");
        assertThat(new PageOutcome.OutOfRange(5, 3).message())
                .isEqualTo("page 5 does not exist: this result has 3 pages");
    }

    private static List<String> bodies(List<Page> pages) {
        List<String> body = new ArrayList<>();
        for (Page page : pages) {
            List<String> lines = page.lines();
            int end = page.total() > 1 ? lines.size() - 1 : lines.size();
            body.addAll(lines.subList(1, end));
        }
        return body;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
