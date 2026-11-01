package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.ArtifactKey;
import java.util.List;

/**
 * The API types of one artifact that have no source file.
 *
 * @param artifact the artifact
 * @param types    its types without sources, sorted by name
 */
public record ArtifactWithoutSources(ArtifactKey artifact, List<TypeWithoutSources> types) {
    public ArtifactWithoutSources {
        types = types == null ? List.of() : List.copyOf(types);
    }
}
