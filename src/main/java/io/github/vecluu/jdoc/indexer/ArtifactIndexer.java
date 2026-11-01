package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Path;

/** Indexes one Maven artifact's binary jar (with its doc jars) into the store, replacing any earlier index. */
@FunctionalInterface
public interface ArtifactIndexer {

    /**
     * Indexes the artifact in a single transaction.
     *
     * @param gav   the artifact
     * @param jar   its binary jar
     * @param stamp the jar's stamp, stored for the refresh rules
     * @throws io.github.vecluu.jdoc.error.JdocException if the jar cannot be read or the index cannot be written
     */
    StoredArtifact index(Gav gav, Path jar, FileStamp stamp);
}
