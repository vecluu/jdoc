package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.ArtifactKey;
import java.util.Objects;

/**
 * An artifact that could not be indexed.
 *
 * @param artifact         the artifact
 * @param reason           one-line reason
 * @param previouslyFailed true if this call did not try again because the same jar (path, mtime, size) already failed
 *                         in an earlier call; it is retried once the jar changes or with {@link IndexOptions#force()}
 */
public record IndexFailure(ArtifactKey artifact, String reason, boolean previouslyFailed) {

    public IndexFailure {
        Objects.requireNonNull(artifact, "artifact");
        reason = reason == null || reason.isBlank() ? "unknown error" : reason.replaceAll("\\s+", " ").strip();
    }
}
