package io.github.vecluu.jdoc.text;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.text.ColumnLayout.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Paginates a realistic ~25k-character class view with the default budget. */
class PaginationScenarioIT {

    private static final String HEADER = "com.example.widgets.WidgetRegistry<K, V>  public final class  (widgets 4.2.1)";
    private static final String COMMAND = "jdoc class com.example.widgets.WidgetRegistry";

    @Test
    void everyPageIsWithinBudgetAndPagesConcatenateToTheFullContent() {
        TextResult result = classView();
        assertThat(result.text().length()).isBetween(24_000, 27_000);

        Paginator paginator = new Paginator();
        List<Page> pages = paginator.paginate(result, COMMAND + " --page 1");

        assertThat(pages).hasSizeGreaterThanOrEqualTo(4);
        List<String> body = new ArrayList<>();
        for (Page page : pages) {
            assertThat(page.printedCharacters()).isLessThanOrEqualTo(TokenBudget.defaultBudget().maxCharacters());
            assertThat(page.lines().getFirst()).isEqualTo(HEADER);
            String footer = page.lines().getLast();
            if (page.hasNext()) {
                assertThat(footer).isEqualTo("page " + page.number() + "/" + pages.size() + " · next: " + COMMAND
                        + " --page " + (page.number() + 1));
            } else {
                assertThat(footer).isEqualTo("page " + pages.size() + "/" + pages.size());
            }
            body.addAll(page.lines().subList(1, page.lines().size() - 1));
        }
        assertThat(body).isEqualTo(result.bodyLines());
        assertThat(paginator.page(result, pages.size() + 1, COMMAND)).isInstanceOf(PageOutcome.OutOfRange.class);
    }

    private static TextResult classView() {
        TextResult.Builder builder = TextResult.builder(HEADER)
                .line("A registry of widgets keyed by name, safe for concurrent use by multiple threads.")
                .line("extends AbstractRegistry<K, V>  implements Registry<K, V>, Iterable<V>")
                .blankLine();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 160; i++) {
            rows.add(Row.of(signature(i), "Returns the widget number " + i
                    + " after applying the configured lookup strategy and caching policy."));
        }
        builder.lines(new ColumnLayout().format(rows));
        builder.blankLine();
        for (int i = 0; i < 12; i++) {
            List<String> doc = new ArrayList<>();
            doc.add("static <W extends Widget> W create" + i + "(String name, Map<String, Object> options)");
            doc.add("Creates widget " + i + " with the given options; options are copied defensively.");
            doc.add("@param name the widget name, unique within the registry");
            doc.add("@param options construction options");
            doc.add("@return the new widget");
            doc.add("");
            builder.block(doc);
        }
        builder.line("inherited from AbstractRegistry: clear, contains, size");
        builder.line("inherited from Object: getClass, notify, notifyAll, wait");
        return builder.build();
    }

    private static String signature(int i) {
        return switch (i % 4) {
            case 0 -> "V get" + i + "(K key)";
            case 1 -> "static <T> Optional<T> lookup" + i + "(Class<T> type, String name)";
            case 2 -> "boolean remove" + i + "(Object key)";
            default -> "void register" + i + "(K key, V value) throws RegistryException";
        };
    }
}
