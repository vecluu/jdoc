package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.IndexedArtifact;

/**
 * The outcome of writing one artifact.
 *
 * @param artifact    the stored artifact row, with its new database id
 * @param typeCount   types written
 * @param memberCount members written
 */
public record StoredArtifact(IndexedArtifact artifact, int typeCount, int memberCount) {
}
