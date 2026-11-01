package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The indexed view of a module's classpath: the artifact ids every read query is scoped to.
 *
 * @param artifactIds   ids in scope, in classpath order, the JDK last
 * @param artifacts     id to artifact key (for labels: {@link ArtifactKey#label()}), in the same order
 * @param jdkArtifactId the JDK's id, if the JDK is indexed
 * @param status        how the classpath was resolved
 * @param warnings      the classpath's user-facing warnings
 */
public record IndexedClasspath(
        List<Long> artifactIds,
        Map<Long, ArtifactKey> artifacts,
        Optional<Long> jdkArtifactId,
        ResolutionStatus status,
        List<String> warnings) {

    public IndexedClasspath {
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
        artifacts = artifacts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(artifacts));
        jdkArtifactId = jdkArtifactId == null ? Optional.empty() : jdkArtifactId;
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** The key of an artifact in scope. */
    public Optional<ArtifactKey> key(long artifactId) {
        return Optional.ofNullable(artifacts.get(artifactId));
    }

    /** The user-facing label of an artifact in scope ({@code commons-lang3 3.17.0}, {@code jdk 25}). */
    public Optional<String> label(long artifactId) {
        return key(artifactId).map(ArtifactKey::label);
    }
}
