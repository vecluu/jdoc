package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Renders one documented section of a javadoc page (the class description or one member detail) into a
 * {@link RenderedDoc}, with the shared text rules of {@link HtmlTextFormatter}, {@link BlockTagFormatter} and
 * {@link FirstSentenceExtractor}.
 *
 * <ul>
 *   <li>Description: the section's {@code div.block}s, except "Description copied from" labels and deprecation
 *       blocks; member links rewritten by {@link JavadocLinkRewriter}.</li>
 *   <li>Block tags from the notes list ({@code dl.notes}, or plain {@code dl} in old layouts), in conventional
 *       comment order: {@code @apiNote}/{@code @implSpec}/{@code @implNote}, {@code @param} (type parameters and
 *       record components first, as javadoc lists them), {@code @return}, {@code @throws}, {@code @see},
 *       {@code @since}, then {@code @deprecated} from the deprecation block (only if it has a comment: javadoc
 *       also marks members of deprecated types). Other notes ("Specified by:", "Overrides:", "Default:", ...)
 *       and generated "See Also: Constant Field Values" links are dropped.</li>
 *   <li>A section with neither description nor tags is {@link RenderedDoc#missing()}.</li>
 * </ul>
 */
final class JavadocSectionRenderer {

    private static final String DEPRECATION_SELECTOR = ".deprecation-block, .deprecationBlock";
    private static final String LEGACY_DEPRECATED_LABEL = "span.deprecatedLabel";
    private static final String COPIED_LABEL_SELECTOR = ".description-from-type-label, .descfrmTypeLabel";

    private final HtmlTextFormatter formatter;
    private final BlockTagFormatter tags;
    private final FirstSentenceExtractor sentences;
    private final JavadocLinkRewriter links;

    JavadocSectionRenderer(HtmlTextFormatter formatter, BlockTagFormatter tags, FirstSentenceExtractor sentences,
                           JavadocLinkRewriter links) {
        this.formatter = formatter;
        this.tags = tags;
        this.sentences = sentences;
        this.links = links;
    }

    /** Renders {@code section} (not modified); {@code pageType} is the type the page documents. */
    RenderedDoc render(Element section, TypeName pageType) {
        Element copy = section.clone();
        links.rewrite(copy, pageType);
        Optional<String> deprecation = deprecation(copy);
        String description = description(copy);
        List<String> blockTags = new ArrayList<>(notes(copy));
        deprecation.ifPresent(blockTags::add);
        if (description.isBlank() && blockTags.isEmpty()) {
            return RenderedDoc.missing();
        }
        return new RenderedDoc(description, sentences.extract(description), blockTags, false);
    }

    private String description(Element section) {
        StringBuilder html = new StringBuilder();
        for (Element block : section.select("div.block")) {
            if (isDescriptionBlock(block, section)) {
                html.append(block.html()).append("\n<p>\n");
            }
        }
        return formatter.format(html.toString());
    }

    private static boolean isDescriptionBlock(Element block, Element section) {
        if (!block.select(COPIED_LABEL_SELECTOR).isEmpty() || !block.select(LEGACY_DEPRECATED_LABEL).isEmpty()) {
            return false;
        }
        for (Element parent = block.parent(); parent != null && parent != section; parent = parent.parent()) {
            String tag = parent.normalName();
            if (tag.equals("dl") || tag.equals("dd") || parent.is(DEPRECATION_SELECTOR) || parent.is("div.block")) {
                return false;
            }
        }
        return true;
    }

    private Optional<String> deprecation(Element section) {
        Element block = section.selectFirst(DEPRECATION_SELECTOR);
        if (block == null) {
            Element label = section.selectFirst(LEGACY_DEPRECATED_LABEL);
            if (label == null) {
                return Optional.empty();
            }
            block = label.parent();
        }
        Element comment = block.selectFirst(".deprecation-comment, .deprecationComment");
        String text = comment == null ? "" : formatter.format(comment.html());
        // javadoc marks members of deprecated types and @Deprecated-only members with an empty block; source
        // rendering has no tag for those
        return text.isBlank() ? Optional.empty() : Optional.of(tags.deprecated(text));
    }

    private List<String> notes(Element section) {
        Elements lists = section.select("dl.notes");
        if (lists.isEmpty()) {
            lists = new Elements(section.children().stream().filter(child -> child.normalName().equals("dl")).toList());
        }
        NoteTags collected = new NoteTags();
        for (Element list : lists) {
            if (list.parents().stream().anyMatch(p -> p.normalName().equals("dl"))) {
                continue;
            }
            String label = "";
            for (Element item : list.children()) {
                if (item.normalName().equals("dt")) {
                    label = item.text().strip().replaceAll(":$", "").toLowerCase(Locale.ROOT);
                } else if (item.normalName().equals("dd")) {
                    note(label, item, collected);
                }
            }
        }
        return collected.ordered();
    }

    private void note(String label, Element dd, NoteTags out) {
        switch (label) {
            case "type parameters" -> out.params.add(named(dd, (name, text) -> tags.param("<" + name + ">", text)));
            case "parameters", "record components" -> out.params.add(named(dd, tags::param));
            case "returns" -> out.returns.add(tags.returns(text(dd)));
            case "throws" -> out.throwsTags.add(named(dd, tags::throwsTag));
            case "since" -> out.since.add(tags.since(text(dd)));
            case "see also" -> seeAlso(dd).forEach(item -> out.see.add(tags.see(item)));
            case "api note" -> out.notes.add(tags.line("apiNote", text(dd)));
            case "implementation requirements" -> out.notes.add(tags.line("implSpec", text(dd)));
            case "implementation note" -> out.notes.add(tags.line("implNote", text(dd)));
            default -> {
                // "Specified by:", "Overrides:", "Default:", "All Implemented Interfaces:", ... are not tags
            }
        }
    }

    private interface NamedTag {
        String format(String name, String text);
    }

    /** {@code <code>name</code> - text} → tag line; the name is the first {@code <code>} element's text. */
    private String named(Element dd, NamedTag tag) {
        Element copy = dd.clone();
        Element code = copy.selectFirst("code");
        String name = "";
        if (code != null) {
            name = code.text().strip();
            code.remove();
        }
        String text = formatter.format(copy.html()).strip();
        if (text.startsWith("-")) {
            text = text.substring(1).strip();
        }
        if (name.isEmpty()) {
            int space = text.indexOf(' ');
            name = space < 0 ? text : text.substring(0, space);
            text = space < 0 ? "" : text.substring(space + 1).replaceFirst("^-\\s*", "");
        }
        return tag.format(name, text);
    }

    private String text(Element dd) {
        return formatter.format(dd.html());
    }

    /** The items of a "See Also" entry: list items, else comma-separated links/text outside parentheses. */
    private List<String> seeAlso(Element dd) {
        dd = dd.clone();
        dd.select("li:has(a[href*=constant-values.html])").remove();
        dd.select("a[href*=constant-values.html]").remove();
        Elements items = dd.select("li");
        if (!items.isEmpty()) {
            return items.stream().map(li -> formatter.format(li.html())).filter(s -> !s.isBlank()).toList();
        }
        return splitOutsideParentheses(formatter.format(dd.html()));
    }

    private static List<String> splitOutsideParentheses(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '<') {
                depth++;
            } else if (c == ')' || c == '>') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i).strip());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).strip());
        return parts.stream().filter(s -> !s.isEmpty()).toList();
    }

    /** Collected tag lines per group, emitted in conventional comment order. */
    private static final class NoteTags {
        final List<String> notes = new ArrayList<>();
        final List<String> params = new ArrayList<>();
        final List<String> returns = new ArrayList<>();
        final List<String> throwsTags = new ArrayList<>();
        final List<String> see = new ArrayList<>();
        final List<String> since = new ArrayList<>();

        List<String> ordered() {
            List<String> all = new ArrayList<>(notes);
            all.addAll(params);
            all.addAll(returns);
            all.addAll(throwsTags);
            all.addAll(see);
            all.addAll(since);
            return all;
        }
    }
}
