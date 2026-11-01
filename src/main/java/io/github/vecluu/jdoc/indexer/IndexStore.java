package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedType;
import java.util.List;
import java.util.Optional;

/** What the indexer needs from the index database; a boundary for tests. */
public interface IndexStore {

    /** The indexed artifact with this key, if any. */
    Optional<IndexedArtifact> find(ArtifactKey key);

    /**
     * Replaces everything stored for {@code artifact.key()} with the artifact and the produced types, in a single
     * transaction (nothing changes if the producer throws).
     */
    StoredArtifact replace(IndexedArtifact artifact, EntryProducer entries);

    /** Types of the artifact whose doc source is neither sources nor JDK source. */
    List<IndexedType> typesWithoutSources(long artifactId);
}
