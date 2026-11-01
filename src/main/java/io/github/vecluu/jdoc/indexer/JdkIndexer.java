package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.config.JdkInfo;
import java.nio.file.Path;

/** Indexes the JDK (key {@code jdk:<feature version>}) into the store, replacing any earlier index. */
public interface JdkIndexer {

    /**
     * Indexes the JDK in a single transaction.
     *
     * @param jdk   the JDK
     * @param stamp the stamp of {@link #indexedFile(JdkInfo)}, stored for the refresh rules
     * @throws io.github.vecluu.jdoc.error.JdocException if the JDK cannot be read or the index cannot be written
     */
    StoredArtifact index(JdkInfo jdk, FileStamp stamp);

    /** The file whose stamp decides whether the JDK index is current: {@code lib/src.zip}, else {@code lib/modules}. */
    default Path indexedFile(JdkInfo jdk) {
        return jdk.srcZip().orElseGet(() -> jdk.javaHome().resolve("lib").resolve("modules"));
    }
}
