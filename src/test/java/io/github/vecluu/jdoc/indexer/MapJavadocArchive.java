package io.github.vecluu.jdoc.indexer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link JavadocArchive} with helpers for minimal JDK 17+ style pages. */
final class MapJavadocArchive implements JavadocArchive {

    private final Map<String, String> pages = new HashMap<>();
    private boolean closed;

    /** Adds a type page with a class description and member sections ({@code id} → description). */
    MapJavadocArchive withTypePage(String path, String description, Map<String, String> members) {
        StringBuilder html = new StringBuilder("<html><body><section class=\"class-description\"><div class=\"block\">")
                .append(description).append("</div></section>");
        members.forEach((id, text) -> html.append("<section class=\"detail\" id=\"").append(id)
                .append("\"><h3>x</h3><div class=\"block\">").append(text).append("</div></section>"));
        pages.put(path, html.append("</body></html>").toString());
        return this;
    }

    @Override
    public Optional<String> page(String entryPath) {
        return Optional.ofNullable(pages.get(entryPath));
    }

    @Override
    public boolean hasPage(String entryPath) {
        return pages.containsKey(entryPath);
    }

    @Override
    public void close() {
        closed = true;
    }

    boolean closed() {
        return closed;
    }
}
