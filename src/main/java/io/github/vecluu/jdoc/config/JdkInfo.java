package io.github.vecluu.jdoc.config;

import io.github.vecluu.jdoc.model.ArtifactKey;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Facts about a JDK installation, see {@link JdkInfoReader}.
 *
 * @param javaHome       the JDK directory
 * @param featureVersion feature release, e.g. {@code 25}
 * @param srcZip         {@code lib/src.zip}, if the JDK ships it
 */
public record JdkInfo(Path javaHome, int featureVersion, Optional<Path> srcZip) {
    public JdkInfo {
        Objects.requireNonNull(javaHome, "java home must not be null");
        if (featureVersion < 1) {
            throw new IllegalArgumentException("invalid feature version: " + featureVersion);
        }
        srcZip = srcZip == null ? Optional.empty() : srcZip;
    }

    /** The artifact key under which this JDK is indexed, {@code jdk:25}. */
    public ArtifactKey artifactKey() {
        return ArtifactKey.jdk(featureVersion);
    }
}
