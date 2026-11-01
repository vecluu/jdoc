package io.github.vecluu.jdoc.javadoc;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Renders a Javadoc comment parsed by javac ({@link DocCommentTree}, obtained through {@code DocTrees}) into compact
 * plain text ({@link RenderedDoc}).
 *
 * <p>Inline tags are resolved here ({@code {@code}}, {@code {@link}}/{@code {@linkplain}} as the reference written
 * in source or the label, {@code {@literal}}, {@code {@value}}, {@code {@summary}}, inline {@code {@return}},
 * {@code {@snippet}} as a {@code <pre>} block); all HTML then goes through the shared {@link HtmlTextFormatter},
 * the first sentence through {@link FirstSentenceExtractor} ({@code {@summary}} wins if present) and block tags
 * through {@link BlockTagFormatter}. Kept block tags: {@code @param}, {@code @return}, {@code @throws}/
 * {@code @exception}, {@code @since}, {@code @deprecated}, {@code @see}, {@code @apiNote}, {@code @implNote},
 * {@code @implSpec}; others ({@code @author}, {@code @serial}, {@code @jls}, ...) are dropped.
 * {@code {@inheritDoc}} anywhere sets {@link RenderedDoc#inheritsDoc()}; resolving it is the caller's job.
 *
 * <p>Stateless and thread-safe.
 */
public final class DocCommentRenderer {

    private static final Set<String> NOTE_TAGS = Set.of("apiNote", "implNote", "implSpec");

    private final HtmlTextFormatter formatter;
    private final FirstSentenceExtractor firstSentences;
    private final BlockTagFormatter blockTags;

    /** A renderer using the shared text rules. */
    public DocCommentRenderer() {
        this(new HtmlTextFormatter(), new FirstSentenceExtractor(), new BlockTagFormatter());
    }

    /** A renderer using the given shared rule instances. */
    public DocCommentRenderer(HtmlTextFormatter formatter, FirstSentenceExtractor firstSentences,
                              BlockTagFormatter blockTags) {
        this.formatter = formatter;
        this.firstSentences = firstSentences;
        this.blockTags = blockTags;
    }

    /** Renders a comment; a {@code null} comment yields {@link RenderedDoc#missing()}. */
    public RenderedDoc render(DocCommentTree comment) {
        if (comment == null) {
            return RenderedDoc.missing();
        }
        DocCommentHtml html = new DocCommentHtml(formatter);
        String description = html.text(comment.getFullBody());
        String firstSentence = html.summary()
                .map(summary -> summary.replaceAll("\\s+", " ").strip())
                .orElseGet(() -> firstSentences.extract(description));
        List<String> lines = new ArrayList<>();
        boolean explicitReturn = false;
        for (DocTree tag : comment.getBlockTags()) {
            explicitReturn |= tag instanceof ReturnTree;
            blockTag(tag, html).ifPresent(lines::add);
        }
        if (!explicitReturn && html.inlineReturn().isPresent()) {
            lines.add(blockTags.returns(html.inlineReturn().get()));
        }
        return new RenderedDoc(description, firstSentence, lines, html.sawInheritDoc());
    }

    /** True if the comment has a {@code @deprecated} block tag. */
    public boolean hasDeprecatedTag(DocCommentTree comment) {
        return comment != null && comment.getBlockTags().stream().anyMatch(DeprecatedTree.class::isInstance);
    }

    /**
     * Plain-text descriptions of the comment's {@code @param} tags for value parameters (not type parameters), by
     * name, in comment order. Used for record components.
     */
    public Map<String, String> parameterDescriptions(DocCommentTree comment) {
        Map<String, String> result = new LinkedHashMap<>();
        if (comment == null) {
            return result;
        }
        for (DocTree tag : comment.getBlockTags()) {
            if (tag instanceof ParamTree param && !param.isTypeParameter()) {
                result.putIfAbsent(param.getName().getName().toString(),
                        oneLine(new DocCommentHtml(formatter).text(param.getDescription())));
            }
        }
        return result;
    }

    private Optional<String> blockTag(DocTree tag, DocCommentHtml html) {
        String line = switch (tag) {
            case ParamTree param -> blockTags.param(paramName(param), html.text(param.getDescription()));
            case ReturnTree ret -> blockTags.returns(html.text(ret.getDescription()));
            case ThrowsTree thrown -> blockTags.throwsTag(
                    DocCommentHtml.reference(thrown.getExceptionName()), html.text(thrown.getDescription()));
            case SinceTree since -> blockTags.since(html.text(since.getBody()));
            case DeprecatedTree deprecated -> blockTags.deprecated(html.text(deprecated.getBody()));
            case SeeTree see -> blockTags.see(html.text(see.getReference()));
            case UnknownBlockTagTree unknown when NOTE_TAGS.contains(unknown.getTagName()) ->
                    blockTags.line(unknown.getTagName(), html.text(unknown.getContent()));
            default -> null;
        };
        return Optional.ofNullable(line);
    }

    private static String paramName(ParamTree param) {
        String name = param.getName().getName().toString();
        return param.isTypeParameter() ? "<" + name + ">" : name;
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }
}
