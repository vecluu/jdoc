package io.github.vecluu.jdoc.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import lombok.Builder;

/**
 * A row of the {@code artifacts} table.
 *
 * @param id         database id
 * @param key        artifact identity
 * @param jarPath    binary jar (for the JDK: its {@code src.zip})
 * @param jarMtime   jar modification time in epoch milliseconds when indexed
 * @param jarSize    jar size in bytes when indexed
 * @param sourcesJar sources jar used, if any
 * @param javadocJar javadoc jar used, if any
 * @param indexedAt  when the artifact was indexed
 */
@Builder(toBuilder = true)
public record IndexedArtifact(
        long id,
        ArtifactKey key,
        Path jarPath,
        long jarMtime,
        long jarSize,
        Optional<Path> sourcesJar,
        Optional<Path> javadocJar,
        Instant indexedAt) {

    public IndexedArtifact {
        Checks.notNull(key, "key");
        Checks.notNull(jarPath, "jar path");
        Checks.notNull(indexedAt, "indexed-at");
        sourcesJar = sourcesJar == null ? Optional.empty() : sourcesJar;
        javadocJar = javadocJar == null ? Optional.empty() : javadocJar;
    }
}
