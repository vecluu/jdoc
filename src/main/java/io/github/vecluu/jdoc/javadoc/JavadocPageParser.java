package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Parses javadoc HTML pages with jsoup. Targets the JDK 17+ layout ({@code section.class-description},
 * {@code section.detail[id]}); older layouts (JDK 8–16: {@code div.description}, anchors {@code <a name|id>}
 * before {@code ul.blockList}) are read where their anchors can be found. Stateless and thread-safe.
 */
public final class JavadocPageParser {

    private static final String TYPE_DESCRIPTION =
            "section.class-description, section.description, div.description";
    private static final String PACKAGE_DESCRIPTION =
            "section.package-description, section.packageDescription, div.packageDescription";

    private final JavadocSectionRenderer renderer;

    /** A parser using the shared text rules. */
    public JavadocPageParser() {
        this(new HtmlTextFormatter(), new BlockTagFormatter(), new FirstSentenceExtractor());
    }

    /** A parser using the given shared text rule instances. */
    public JavadocPageParser(HtmlTextFormatter formatter, BlockTagFormatter tags, FirstSentenceExtractor sentences) {
        this.renderer = new JavadocSectionRenderer(formatter, tags, sentences, new JavadocLinkRewriter());
    }

    /** Parses the page of {@code type} ({@code a/b/Outer.Inner.html}). */
    public JavadocTypePage parseTypePage(String html, TypeName type) {
        Document document = Jsoup.parse(html);
        Element description = document.selectFirst(TYPE_DESCRIPTION);
        if (description != null && description.normalName().equals("div")) {
            Element item = description.selectFirst("ul.blockList > li.blockList");
            description = item == null ? description : item;
        }
        RenderedDoc typeDoc = description == null ? RenderedDoc.missing() : renderer.render(description, type);
        List<JavadocMemberDoc> members = new ArrayList<>(modernSections(document, type));
        if (members.isEmpty()) {
            members.addAll(legacySections(document, type));
        }
        return new JavadocTypePage(type, typeDoc, members);
    }

    /** Parses a {@code package-summary.html} page into the package doc. */
    public RenderedDoc parsePackagePage(String html, String packageName) {
        Document document = Jsoup.parse(html);
        TypeName context = TypeName.of(packageName, List.of("package-info"));
        Element description = document.selectFirst(PACKAGE_DESCRIPTION);
        if (description != null) {
            return renderer.render(description, context);
        }
        Element anchor = document.selectFirst("a[name=package.description], a[id=package.description]");
        if (anchor != null && anchor.parent() != null) {
            Element container = anchor.parent().clone();
            container.select("table, ul.blockList, .summary").remove();
            return renderer.render(container, context);
        }
        return RenderedDoc.missing();
    }

    private List<JavadocMemberDoc> modernSections(Document document, TypeName type) {
        List<JavadocMemberDoc> members = new ArrayList<>();
        for (Element section : document.select("section.detail[id]")) {
            Set<String> ids = new LinkedHashSet<>();
            ids.add(section.id());
            section.select("h3[id], h4[id]").forEach(heading -> ids.add(heading.id()));
            List<JavadocAnchor> anchors = parseAll(ids);
            if (!anchors.isEmpty()) {
                members.add(new JavadocMemberDoc(anchors, renderer.render(section, type)));
            }
        }
        return members;
    }

    /** JDK 8–16: one or more {@code <a name|id>} anchors right before a {@code ul.blockList} detail list. */
    private List<JavadocMemberDoc> legacySections(Document document, TypeName type) {
        List<JavadocMemberDoc> members = new ArrayList<>();
        for (Element list : document.select("ul.blockList, ul.blockListLast")) {
            Set<String> ids = new LinkedHashSet<>();
            for (Element previous = list.previousElementSibling();
                 previous != null && previous.normalName().equals("a");
                 previous = previous.previousElementSibling()) {
                anchorId(previous).ifPresent(ids::add);
            }
            List<JavadocAnchor> anchors = parseAll(ids);
            Element item = list.selectFirst("> li");
            if (!anchors.isEmpty() && item != null) {
                members.add(new JavadocMemberDoc(anchors, renderer.render(item, type)));
            }
        }
        return members;
    }

    private static Optional<String> anchorId(Element anchor) {
        String id = anchor.hasAttr("id") ? anchor.attr("id") : anchor.attr("name");
        return id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    private static List<JavadocAnchor> parseAll(Set<String> ids) {
        return ids.stream().map(JavadocAnchor::parse).flatMap(Optional::stream).toList();
    }
}
