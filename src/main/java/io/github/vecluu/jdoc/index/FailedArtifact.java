package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.ArtifactKey;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A row of the {@code failed_artifacts} table: an artifact whose jar could not be indexed, remembered with the jar's
 * path, modification time and size at the time of the failure so that an unchanged jar is not retried.
 *
 * @param key      artifact identity (at most one failure is remembered per artifact)
 * @param jarPath  the jar that failed (for the JDK: its {@code src.zip})
 * @param jarMtime jar modification time in epoch milliseconds when it failed
 * @param jarSize  jar size in bytes when it failed
 * @param reason   one-line reason (whitespace runs are collapsed to one space)
 */
public record FailedArtifact(ArtifactKey key, Path jarPath, long jarMtime, long jarSize, String reason) {

    public FailedArtifact {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(jarPath, "jar path");
        reason = reason == null || reason.isBlank() ? "unknown error" : reason.replaceAll("\\s+", " ").strip();
    }

    /** True if the failure was recorded for exactly this jar file state (path, mtime and size). */
    public boolean matches(Path path, long mtimeMillis, long size) {
        return jarPath.equals(path) && jarMtime == mtimeMillis && jarSize == size;
    }
}
