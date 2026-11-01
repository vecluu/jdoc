package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.ArtifactKey;
import java.util.Optional;

/** The {@code failed_artifacts} table: artifacts whose last indexing attempt failed. */
public interface FailedArtifactRepository {

    /** The remembered failure of this artifact, if any. */
    Optional<FailedArtifact> find(ArtifactKey key);

    /** Remembers a failure, replacing an earlier one of the same artifact. */
    void save(FailedArtifact failure);

    /**
     * Forgets the failure of an artifact.
     *
     * @return true if a failure was remembered
     */
    boolean delete(ArtifactKey key);
}
