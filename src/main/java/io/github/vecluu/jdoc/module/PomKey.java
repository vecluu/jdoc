package io.github.vecluu.jdoc.module;

import java.util.List;
import java.util.Objects;

/**
 * The cache key of a module's resolved classpath.
 *
 * @param value        lowercase hex SHA-256
 * @param fingerprints the inputs that went into the key, module POM first
 */
public record PomKey(String value, List<PomFingerprint> fingerprints) {
    public PomKey {
        Objects.requireNonNull(value, "value must not be null");
        fingerprints = fingerprints == null ? List.of() : List.copyOf(fingerprints);
    }
}
