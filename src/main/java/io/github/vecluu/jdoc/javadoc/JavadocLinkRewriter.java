package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.model.TypeName;
import java.util.Optional;
import org.jsoup.nodes.Element;

/**
 * Rewrites the text of member links in javadoc HTML to the {@code Type#member(params)} form that doc-comment
 * rendering produces from source ({@code {@link Foo#bar(int)}} → {@code Foo#bar(int)}).
 *
 * <p>javadoc labels a generated member link {@code Foo.bar(int)} (other page) or {@code bar(int)} (same page). A
 * link is rewritten only if its {@code href} fragment is a member anchor and its text is exactly such a generated
 * label for that member: {@code Foo.bar(int)} → {@code Foo#bar(int)}, same-page {@code bar(int)} →
 * {@code #bar(int)}, {@code Point.ORIGIN} → {@code Point#ORIGIN}. Explicit labels ({@code {@link #area() area}})
 * stay unchanged. Stateless.
 */
final class JavadocLinkRewriter {

    /** Rewrites every member link below {@code root} in place; {@code pageType} is the page being read. */
    void rewrite(Element root, TypeName pageType) {
        for (Element link : root.select("a[href]")) {
            rewritten(link.attr("href"), link.text().strip(), pageType).ifPresent(link::text);
        }
    }

    /** The rewritten label for a link, or empty if the link is not a generated member link. */
    Optional<String> rewritten(String href, String text, TypeName pageType) {
        int hash = href.indexOf('#');
        if (hash < 0 || text.isEmpty()) {
            return Optional.empty();
        }
        Optional<JavadocAnchor> parsed = JavadocAnchor.parse(href.substring(hash + 1));
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        JavadocAnchor anchor = parsed.get();
        String page = href.substring(0, hash);
        boolean samePage = page.isEmpty();
        String typeName = samePage ? pageType.relativeName() : pageTypeName(page);
        String memberName = anchor.name().equals(JavadocAnchor.CONSTRUCTOR_NAME)
                ? typeName.substring(typeName.lastIndexOf('.') + 1)
                : anchor.name();

        String prefix;
        String rest;
        if (text.startsWith(memberName) && isMemberRest(text.substring(memberName.length()), anchor)) {
            prefix = "";
            rest = text;
        } else {
            int at = text.indexOf("." + memberName);
            while (at >= 0 && !isMemberRest(text.substring(at + 1 + memberName.length()), anchor)) {
                at = text.indexOf("." + memberName, at + 1);
            }
            if (at <= 0 || text.substring(0, at).contains("(")) {
                return Optional.empty();
            }
            prefix = text.substring(0, at);
            rest = text.substring(at + 1);
        }
        if (prefix.isEmpty()) {
            return Optional.of(samePage ? "#" + rest : typeName + "#" + rest);
        }
        return Optional.of(prefix + "#" + rest);
    }

    private static boolean isMemberRest(String rest, JavadocAnchor anchor) {
        if (anchor.isField()) {
            return rest.isEmpty();
        }
        return rest.startsWith("(") && rest.endsWith(")");
    }

    /** {@code ../util/Map.Entry.html} → {@code Map.Entry}. */
    private static String pageTypeName(String page) {
        String file = page.substring(page.lastIndexOf('/') + 1);
        return file.endsWith(".html") ? file.substring(0, file.length() - ".html".length()) : file;
    }
}
