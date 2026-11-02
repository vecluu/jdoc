package io.github.vecluu.jdoc.javadoc;

import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.List;
import java.util.Optional;

/**
 * Docs from a javadoc tree: locates a type's page ({@code a/b/Outer.Inner.html}) or a package page
 * ({@code a/b/package-summary.html}) in a {@link JavadocPageSource} and parses it with {@link JavadocPageParser}.
 */
public final class JavadocJarReader {

    private static final String PACKAGE_PAGE = "package-summary.html";

    private final JavadocPageSource pages;
    private final JavadocPageParser parser;

    public JavadocJarReader(JavadocPageSource pages, JavadocPageParser parser) {
        this.pages = pages;
        this.parser = parser;
    }

    /** Entry path of a type's page, {@code java/util/Map.Entry.html}. */
    public static String typePagePath(TypeName type) {
        return type.javadocPagePath();
    }

    /** Entry path of a package page, {@code java/util/package-summary.html}. */
    public static String packagePagePath(String packageName) {
        return packageName.isEmpty() ? PACKAGE_PAGE : packageName.replace('.', '/') + "/" + PACKAGE_PAGE;
    }

    /**
     * The parsed page of a type, if the tree has one. For nested types, an {@code @since} that javadoc copied from
     * the enclosing type's description (recent javadoc versions do) is removed again, as source rendering has none.
     */
    public Optional<JavadocTypePage> typePage(TypeName type) {
        return parsedPage(type).map(this::withoutCopiedSince);
    }

    private Optional<JavadocTypePage> parsedPage(TypeName type) {
        return pages.page(typePagePath(type)).map(html -> parser.parseTypePage(html, type));
    }

    private JavadocTypePage withoutCopiedSince(JavadocTypePage page) {
        Optional<TypeName> outer = page.type().outerName();
        Optional<String> since = sinceTag(page.typeDoc());
        if (outer.isEmpty() || since.isEmpty()) {
            return page;
        }
        Optional<String> outerSince = parsedPage(outer.get()).flatMap(outerPage -> sinceTag(outerPage.typeDoc()));
        if (!outerSince.equals(since)) {
            return page;
        }
        RenderedDoc doc = page.typeDoc();
        List<String> tags = doc.blockTags().stream().filter(tag -> !tag.equals(since.get())).toList();
        return new JavadocTypePage(page.type(),
                new RenderedDoc(doc.description(), doc.firstSentence(), tags, doc.inheritsDoc()), page.members());
    }

    private static Optional<String> sinceTag(RenderedDoc doc) {
        return doc.blockTags().stream().filter(tag -> tag.startsWith("@since")).findFirst();
    }

    /** The package doc from {@code package-summary.html}, if the page exists and has a description. */
    public Optional<RenderedDoc> packageDoc(String packageName) {
        return pages.page(packagePagePath(packageName))
                .map(html -> parser.parsePackagePage(html, packageName))
                .filter(doc -> !doc.isEmpty());
    }
}
