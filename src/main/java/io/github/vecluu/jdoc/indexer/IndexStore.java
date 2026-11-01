package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.index.FailedArtifact;
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
     * transaction (nothing changes if the producer throws). A remembered failure of the artifact is forgotten.
     */
    StoredArtifact replace(IndexedArtifact artifact, EntryProducer entries);

    /** The remembered failure of this artifact, if its last indexing attempt failed. */
    Optional<FailedArtifact> findFailure(ArtifactKey key);

    /** Remembers that an artifact failed to index, replacing an earlier failure of it. */
    void recordFailure(FailedArtifact failure);

    /** Types of the artifact whose doc source is neither sources nor JDK source. */
    List<IndexedType> typesWithoutSources(long artifactId);
}
