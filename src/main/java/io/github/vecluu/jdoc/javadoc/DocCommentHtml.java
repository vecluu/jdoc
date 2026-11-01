package io.github.vecluu.jdoc.javadoc;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.EscapeTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.RawTextTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SnippetTree;
import com.sun.source.doctree.SummaryTree;
import com.sun.source.doctree.SystemPropertyTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.doctree.ValueTree;
import java.util.List;
import java.util.Optional;

/**
 * Turns the body of a doc comment (a list of {@link DocTree} nodes) back into an HTML fragment in which every inline
 * tag is replaced by plain HTML, so that {@link HtmlTextFormatter} can apply the shared text rules:
 * {@code {@code x}} becomes {@code <code>x</code>}, {@code {@link Foo#bar(int)}} becomes a link whose text is the
 * reference as written (or the label), {@code {@literal x}} becomes escaped text, snippets become {@code <pre>}.
 *
 * <p>One instance converts one comment and remembers what it saw: whether {@code {@inheritDoc}} occurred, the
 * {@code {@summary}} text and an inline {@code {@return}}. Not thread-safe; cheap to create.
 */
final class DocCommentHtml {

    private final HtmlTextFormatter formatter;
    private boolean inheritDoc;
    private String summary;
    private String inlineReturn;

    DocCommentHtml(HtmlTextFormatter formatter) {
        this.formatter = formatter;
    }

    /** HTML for a list of doc-tree nodes. */
    String html(List<? extends DocTree> trees) {
        StringBuilder sb = new StringBuilder();
        for (DocTree tree : trees) {
            append(tree, sb);
        }
        return sb.toString();
    }

    /** Plain text (shared formatter rules) for a list of doc-tree nodes. */
    String text(List<? extends DocTree> trees) {
        return formatter.format(html(trees));
    }

    boolean sawInheritDoc() {
        return inheritDoc;
    }

    /** Plain text of the first {@code {@summary}} tag, if any. */
    Optional<String> summary() {
        return Optional.ofNullable(summary);
    }

    /** Plain text of the first inline {@code {@return}} tag, if any. */
    Optional<String> inlineReturn() {
        return Optional.ofNullable(inlineReturn);
    }

    private void append(DocTree tree, StringBuilder sb) {
        switch (tree.getKind()) {
            case TEXT -> sb.append(((TextTree) tree).getBody());
            case ENTITY -> sb.append('&').append(((EntityTree) tree).getName()).append(';');
            case START_ELEMENT, END_ELEMENT -> sb.append(tree);
            case CODE -> code(((LiteralTree) tree).getBody().getBody(), sb);
            case LITERAL -> sb.append(escape(((LiteralTree) tree).getBody().getBody()));
            case LINK, LINK_PLAIN -> link((LinkTree) tree, sb);
            case VALUE -> value((ValueTree) tree, sb);
            case INHERIT_DOC -> inheritDoc = true;
            case SUMMARY -> summary((SummaryTree) tree, sb);
            case RETURN -> inlineReturn((ReturnTree) tree, sb);
            case INDEX -> sb.append(escape(signatureOrText(((IndexTree) tree).getSearchTerm())));
            case SYSTEM_PROPERTY -> code(((SystemPropertyTree) tree).getPropertyName().toString(), sb);
            case SNIPPET -> snippet((SnippetTree) tree, sb);
            case UNKNOWN_INLINE_TAG -> sb.append(html(((UnknownInlineTagTree) tree).getContent()));
            case ERRONEOUS -> sb.append(((ErroneousTree) tree).getBody());
            case ESCAPE -> sb.append(escape(((EscapeTree) tree).getBody()));
            case MARKDOWN -> sb.append(((RawTextTree) tree).getContent());
            case REFERENCE -> sb.append(escape(((ReferenceTree) tree).getSignature()));
            default -> {
                // comments, doc root and other tags without visible text
            }
        }
    }

    private static void code(String body, StringBuilder sb) {
        sb.append("<code>").append(escape(body)).append("</code>");
    }

    private void link(LinkTree link, StringBuilder sb) {
        sb.append("<a>");
        if (link.getLabel().isEmpty() || text(link.getLabel()).isBlank()) {
            sb.append(escape(reference(link.getReference())));
        } else {
            sb.append(html(link.getLabel()));
        }
        sb.append("</a>");
    }

    private static void value(ValueTree value, StringBuilder sb) {
        if (value.getReference() != null) {
            sb.append(escape(reference(value.getReference())));
        }
    }

    private void summary(SummaryTree tree, StringBuilder sb) {
        String html = html(tree.getSummary());
        if (summary == null) {
            summary = formatter.format(html);
        }
        sb.append(html);
    }

    private void inlineReturn(ReturnTree tree, StringBuilder sb) {
        if (!tree.isInline()) {
            return;
        }
        String html = html(tree.getDescription());
        String text = formatter.format(html);
        if (inlineReturn == null) {
            inlineReturn = text;
        }
        sb.append("Returns ").append(html);
        if (!text.endsWith(".")) {
            sb.append('.');
        }
    }

    private static void snippet(SnippetTree snippet, StringBuilder sb) {
        if (snippet.getBody() != null) {
            sb.append("<pre>").append(escape(snippet.getBody().getBody())).append("</pre>");
        }
    }

    /** The reference as written, with whitespace collapsed: {@code Foo#bar(int)}. */
    static String reference(ReferenceTree reference) {
        return reference == null ? "" : reference.getSignature().replaceAll("\\s+", " ").strip();
    }

    private static String signatureOrText(DocTree tree) {
        if (tree instanceof TextTree text) {
            return text.getBody();
        }
        return tree.toString();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
