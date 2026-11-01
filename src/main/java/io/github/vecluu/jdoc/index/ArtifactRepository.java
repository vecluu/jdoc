package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The {@code artifacts} table: one row per indexed jar (or the JDK). */
public interface ArtifactRepository {

    /** The artifact with this key, if indexed. */
    Optional<IndexedArtifact> findByKey(ArtifactKey key);

    /** Artifacts indexed from this jar path (normally at most one), ordered by id. */
    List<IndexedArtifact> findByJarPath(Path jarPath);

    /** The artifacts with these ids that exist, ordered by id. */
    List<IndexedArtifact> findByIds(Collection<Long> ids);

    /** Every indexed artifact, ordered by key. */
    List<IndexedArtifact> findAll();

    /**
     * Inserts an artifact row, replacing an existing row with the same key (and, by cascade, all its types,
     * supertypes and members). The given id is ignored.
     *
     * @return the stored artifact with its new id
     */
    IndexedArtifact save(IndexedArtifact artifact);

    /**
     * Deletes an artifact with all its types, supertypes and members.
     *
     * @return true if a row was deleted
     */
    boolean delete(long id);
}
