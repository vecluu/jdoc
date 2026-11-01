package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.javadoc.JavadocTypePage;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.util.Optional;

/** Parsed pages of {@code -javadoc.jar}s; a boundary for tests. */
public interface JavadocDocs {

    /**
     * The parsed page of a type.
     *
     * @return the page, or empty if the javadoc jar has none for the type
     * @throws io.github.vecluu.jdoc.error.JarReadException if the jar cannot be read
     */
    Optional<JavadocTypePage> typePage(Path javadocJar, TypeName type);

    /**
     * The package description from {@code package-summary.html}.
     *
     * @return the doc, or empty if there is no such page or it has no description
     * @throws io.github.vecluu.jdoc.error.JarReadException if the jar cannot be read
     */
    Optional<RenderedDoc> packageDoc(Path javadocJar, String packageName);
}
