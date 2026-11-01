package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.Objects;

/**
 * A package's documentation and the artifact it was read from.
 *
 * @param doc      the rendered {@code package-info} comment or {@code package-summary.html} description
 * @param artifact the artifact whose sources or javadoc jar had it
 */
public record PackageDoc(RenderedDoc doc, ScopedArtifact artifact) {
    public PackageDoc {
        Objects.requireNonNull(doc, "doc");
        Objects.requireNonNull(artifact, "artifact");
    }
}
