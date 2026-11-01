package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.javadoc.JavadocTypePage;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link JavadocDocs}: pages and package docs registered by the test, keyed by type or package. */
final class FakeJavadocDocs implements JavadocDocs {

    private final Map<TypeName, JavadocTypePage> pages = new HashMap<>();
    private final Map<String, RenderedDoc> packages = new HashMap<>();

    FakeJavadocDocs page(JavadocTypePage page) {
        pages.put(page.type(), page);
        return this;
    }

    FakeJavadocDocs packageDoc(String packageName, RenderedDoc doc) {
        packages.put(packageName, doc);
        return this;
    }

    @Override
    public Optional<JavadocTypePage> typePage(Path javadocJar, TypeName type) {
        return Optional.ofNullable(pages.get(type));
    }

    @Override
    public Optional<RenderedDoc> packageDoc(Path javadocJar, String packageName) {
        return Optional.ofNullable(packages.get(packageName));
    }
}
