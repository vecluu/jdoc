package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.javadoc.JavadocJar;
import io.github.vecluu.jdoc.javadoc.JavadocJarReader;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.javadoc.JavadocTypePage;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@link JavadocDocs} reading real javadoc jars with T05's {@link JavadocJarReader}. Parsed type pages are cached
 * for the lifetime of the instance (one call); jars are opened per read. Not thread-safe.
 */
public final class JarJavadocDocs implements JavadocDocs {

    private final JavadocPageParser parser;
    private final Map<String, Optional<JavadocTypePage>> pages = new HashMap<>();

    public JarJavadocDocs(JavadocPageParser parser) {
        this.parser = parser;
    }

    /** Uses the default page parser. */
    public JarJavadocDocs() {
        this(new JavadocPageParser());
    }

    @Override
    public Optional<JavadocTypePage> typePage(Path javadocJar, TypeName type) {
        String key = javadocJar + "!" + type.binaryName();
        Optional<JavadocTypePage> cached = pages.get(key);
        if (cached != null) {
            return cached;
        }
        Optional<JavadocTypePage> page = withReader(javadocJar, reader -> reader.typePage(type));
        pages.put(key, page);
        return page;
    }

    @Override
    public Optional<RenderedDoc> packageDoc(Path javadocJar, String packageName) {
        return withReader(javadocJar, reader -> reader.packageDoc(packageName));
    }

    private <T> T withReader(Path jar, Function<JavadocJarReader, T> work) {
        try (JavadocJar pagesOfJar = JavadocJar.open(jar)) {
            return work.apply(new JavadocJarReader(pagesOfJar, parser));
        }
    }
}
