package io.github.vecluu.jdoc.module;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Contents of {@code modules/<key>/meta.json}: which module a resolved list belongs to and when it was resolved.
 *
 * @param moduleDirectory absolute module directory
 * @param pomKey          the POM key the list was resolved for
 * @param fingerprints    the POM key's inputs
 * @param resolvedAt      when Maven produced the list
 */
public record ModuleMeta(Path moduleDirectory, String pomKey, List<PomFingerprint> fingerprints,
                         Instant resolvedAt) {
    public ModuleMeta {
        Objects.requireNonNull(moduleDirectory, "module directory must not be null");
        Objects.requireNonNull(pomKey, "pom key must not be null");
        Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
        fingerprints = fingerprints == null ? List.of() : List.copyOf(fingerprints);
    }
}
