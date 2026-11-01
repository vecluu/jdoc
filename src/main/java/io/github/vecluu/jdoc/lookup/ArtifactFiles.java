package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.index.ArtifactRepository;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The stored jar paths of artifacts (binary, sources, javadoc), cached by id. Not thread-safe. */
public final class ArtifactFiles {

    private final ArtifactRepository artifacts;
    private final Map<Long, Optional<IndexedArtifact>> cache = new HashMap<>();

    public ArtifactFiles(ArtifactRepository artifacts) {
        this.artifacts = artifacts;
    }

    /** The artifact row with this id, if it exists. */
    public Optional<IndexedArtifact> artifact(long id) {
        return cache.computeIfAbsent(id, key -> artifacts.findByIds(List.of(key)).stream().findFirst());
    }
}
