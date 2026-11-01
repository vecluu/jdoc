package io.github.vecluu.jdoc.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a {@link TextResult} into pages that each fit the {@link TokenBudget}.
 *
 * <p>A result that fits the budget is a single page without footer. Otherwise every page starts with the result's
 * header line, carries as many body lines as fit, and ends with a footer (see {@link PageFooter}); header and footer
 * count against the budget. Blocks are not split across pages unless a block is larger than a whole page, in which
 * case it is split by lines. A single line longer than a page is put on a page of its own (it cannot be shortened
 * without losing content), so only such pages can exceed the budget. Concatenating the body lines of all pages gives
 * back the body of the result.
 */
public final class Paginator {

    private final TokenBudget budget;

    /** A paginator with the default budget of {@value TokenBudget#DEFAULT_MAX_TOKENS} tokens. */
    public Paginator() {
        this(TokenBudget.defaultBudget());
    }

    /** A paginator with the given budget. */
    public Paginator(TokenBudget budget) {
        this.budget = budget;
    }

    /** The budget pages are cut to. */
    public TokenBudget budget() {
        return budget;
    }

    /**
     * All pages of a result.
     *
     * @param result      the result
     * @param commandLine the command line that produced it, used in the "next" footer; {@code --page} is removed
     */
    public List<Page> paginate(TextResult result, String commandLine) {
        PageFooter footer = new PageFooter(commandLine);
        if (printedCharacters(result) <= budget.maxCharacters()) {
            return List.of(new Page(1, 1, result.allLines()));
        }
        List<List<String>> bodies = splitWithFooterReserve(result, footer);
        if (bodies.size() <= 1) {
            return List.of(new Page(1, 1, result.allLines()));
        }
        int total = bodies.size();
        List<Page> pages = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            List<String> lines = new ArrayList<>(bodies.get(i).size() + 2);
            lines.add(result.header());
            lines.addAll(bodies.get(i));
            lines.add(footer.footer(i + 1, total));
            pages.add(new Page(i + 1, total, lines));
        }
        return List.copyOf(pages);
    }

    /**
     * One page of a result; an out-of-range number (including zero and negative numbers) yields
     * {@link PageOutcome.OutOfRange}.
     */
    public PageOutcome page(TextResult result, int pageNumber, String commandLine) {
        List<Page> pages = paginate(result, commandLine);
        if (pageNumber < 1 || pageNumber > pages.size()) {
            return new PageOutcome.OutOfRange(pageNumber, pages.size());
        }
        return new PageOutcome.Shown(pages.get(pageNumber - 1));
    }

    private List<List<String>> splitWithFooterReserve(TextResult result, PageFooter footer) {
        int assumedTotal = 9;
        while (true) {
            int capacity = budget.maxCharacters() - Lines.characterCount(result.header())
                    - footer.maxFooterCharacters(assumedTotal);
            List<List<String>> bodies = split(result.blocks(), capacity);
            if (bodies.size() <= assumedTotal) {
                return bodies;
            }
            assumedTotal = assumedTotal * 10 + 9;
        }
    }

    private static List<List<String>> split(List<TextBlock> blocks, int capacity) {
        PageFiller filler = new PageFiller(Math.max(capacity, 1));
        for (TextBlock block : blocks) {
            filler.add(block);
        }
        return filler.finish();
    }

    private static int printedCharacters(TextResult result) {
        return Lines.characterCount(result.header())
                + result.blocks().stream().mapToInt(TextBlock::characterCount).sum();
    }

    /** Mutable helper that fills page bodies up to a fixed capacity. */
    private static final class PageFiller {
        private final int capacity;
        private final List<List<String>> bodies = new ArrayList<>();
        private List<String> current = new ArrayList<>();
        private int used;

        PageFiller(int capacity) {
            this.capacity = capacity;
        }

        void add(TextBlock block) {
            int size = block.characterCount();
            if (size <= capacity - used) {
                block.lines().forEach(this::append);
            } else if (size <= capacity) {
                flush();
                block.lines().forEach(this::append);
            } else {
                block.lines().forEach(this::addLine);
            }
        }

        private void addLine(String line) {
            if (Lines.characterCount(line) > capacity - used) {
                flush();
            }
            append(line);
        }

        private void append(String line) {
            current.add(line);
            used += Lines.characterCount(line);
        }

        private void flush() {
            if (!current.isEmpty()) {
                bodies.add(List.copyOf(current));
                current = new ArrayList<>();
                used = 0;
            }
        }

        List<List<String>> finish() {
            flush();
            return bodies;
        }
    }
}
