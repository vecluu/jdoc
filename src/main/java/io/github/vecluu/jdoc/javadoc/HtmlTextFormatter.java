package io.github.vecluu.jdoc.javadoc;

import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Turns an HTML fragment, as found in Javadoc comments and javadoc pages, into compact plain text.
 *
 * <ul>
 *   <li>{@code <code>} and {@code <tt>} become backticks (not inside {@code <pre>} or links);</li>
 *   <li>{@code <a>} becomes its plain text, without backticks;</li>
 *   <li>{@code <p>} and headings become a blank line; {@code <br>}, {@code <div>}, table rows a line break;</li>
 *   <li>{@code <pre>} content is kept verbatim (tags inside it dropped, common indentation and leading/trailing
 *       blank lines removed) as its own paragraph;</li>
 *   <li>{@code <ul>}/{@code <ol>} items become {@code - item} lines, nested lists indented by two spaces;
 *       {@code <dt>} is a line, {@code <dd>} an indented line;</li>
 *   <li>all other tags are stripped, entities decoded (non-breaking spaces become spaces), and whitespace
 *       collapsed outside {@code <pre>}; no trailing whitespace, at most one blank line in a row.</li>
 * </ul>
 *
 * <p>Instances are stateless and thread-safe.
 */
public final class HtmlTextFormatter {

    private static final Set<String> CODE_TAGS = Set.of("code", "tt");
    private static final Set<String> SKIPPED_TAGS = Set.of("script", "style", "head", "noscript", "template");
    private static final Set<String> PARAGRAPH_TAGS = Set.of(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "table", "hr", "figure");
    private static final Set<String> LINE_TAGS = Set.of(
            "div", "section", "article", "header", "footer", "main", "nav", "aside", "tr", "thead", "tbody",
            "tfoot", "caption", "dl", "ul", "ol", "dt", "summary", "details");
    private static final Set<String> CELL_TAGS = Set.of("td", "th");

    /** Formats an HTML fragment; {@code null} or blank input yields an empty string. */
    public String format(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Element body = Jsoup.parseBodyFragment(html).body();
        TextBuilder out = new TextBuilder();
        renderChildren(body, out, 0);
        return out.result();
    }

    private void renderChildren(Node parent, TextBuilder out, int listDepth) {
        for (Node child : parent.childNodes()) {
            render(child, out, listDepth);
        }
    }

    private void render(Node node, TextBuilder out, int listDepth) {
        if (node instanceof TextNode text) {
            out.text(text.getWholeText());
        } else if (node instanceof Element element) {
            renderElement(element, out, listDepth);
        }
    }

    private void renderElement(Element element, TextBuilder out, int listDepth) {
        String tag = element.normalName();
        if (SKIPPED_TAGS.contains(tag)) {
            return;
        }
        switch (tag) {
            case "br" -> out.lineBreak();
            case "pre" -> {
                out.paragraphBreak();
                out.verbatim(PreBlock.normalize(rawText(element)));
                out.paragraphBreak();
            }
            case "a" -> out.text(" ".repeat(leadingSpace(element)) + element.text()
                    + " ".repeat(trailingSpace(element)));
            case "ul", "ol" -> {
                out.lineBreak();
                renderChildren(element, out, listDepth + 1);
                out.lineBreak();
            }
            case "li" -> {
                out.lineBreak();
                out.linePrefix("  ".repeat(Math.max(0, listDepth - 1)) + "- ");
                renderChildren(element, out, listDepth);
                out.lineBreak();
            }
            case "dd" -> {
                out.lineBreak();
                out.linePrefix("  ");
                renderChildren(element, out, listDepth);
                out.lineBreak();
            }
            default -> renderGeneric(element, tag, out, listDepth);
        }
    }

    private void renderGeneric(Element element, String tag, TextBuilder out, int listDepth) {
        if (CODE_TAGS.contains(tag)) {
            String code = element.text().strip();
            if (!code.isEmpty()) {
                out.text(" ".repeat(leadingSpace(element)) + "`" + code + "`" + " ".repeat(trailingSpace(element)));
            }
        } else if (PARAGRAPH_TAGS.contains(tag)) {
            out.paragraphBreak();
            renderChildren(element, out, listDepth);
            out.paragraphBreak();
        } else if (LINE_TAGS.contains(tag)) {
            out.lineBreak();
            renderChildren(element, out, listDepth);
            out.lineBreak();
        } else if (CELL_TAGS.contains(tag)) {
            out.text(" ");
            renderChildren(element, out, listDepth);
            out.text(" ");
        } else {
            renderChildren(element, out, listDepth);
        }
    }

    /** 1 if the element's text starts with whitespace that {@link Element#text()} would drop. */
    private static int leadingSpace(Element element) {
        String whole = element.wholeText();
        return !whole.isEmpty() && isSpace(whole.charAt(0)) ? 1 : 0;
    }

    private static int trailingSpace(Element element) {
        String whole = element.wholeText();
        return !whole.isEmpty() && isSpace(whole.charAt(whole.length() - 1)) ? 1 : 0;
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || c == ' ';
    }

    /** All text below a node, unmodified, with {@code <br>} as newline. */
    private static String rawText(Node node) {
        StringBuilder sb = new StringBuilder();
        appendRaw(node, sb);
        return sb.toString();
    }

    private static void appendRaw(Node node, StringBuilder sb) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode text) {
                sb.append(text.getWholeText());
            } else if (child instanceof Element element) {
                if (element.normalName().equals("br")) {
                    sb.append('\n');
                } else {
                    appendRaw(element, sb);
                }
            }
        }
    }
}
