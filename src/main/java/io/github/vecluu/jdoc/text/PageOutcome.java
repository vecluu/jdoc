package io.github.vecluu.jdoc.text;

/**
 * Result of asking for one page: either the page or an out-of-range answer, which callers treat as "not found".
 */
public sealed interface PageOutcome permits PageOutcome.Shown, PageOutcome.OutOfRange {

    /**
     * The requested page exists.
     *
     * @param page the page
     */
    record Shown(Page page) implements PageOutcome {
    }

    /**
     * The requested page does not exist.
     *
     * @param requested the requested page number
     * @param total     the number of pages the result has
     */
    record OutOfRange(int requested, int total) implements PageOutcome {

        /** The message to show the user, e.g. {@code page 5 does not exist: this result has 3 pages}. */
        public String message() {
            return "page " + requested + " does not exist: this result has " + total
                    + (total == 1 ? " page" : " pages");
        }
    }
}
