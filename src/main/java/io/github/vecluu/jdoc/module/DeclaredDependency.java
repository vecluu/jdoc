package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.Scope;
import java.util.Objects;
import java.util.Optional;

/**
 * A dependency declared directly in a POM's {@code <dependencies>} (not {@code dependencyManagement}). Used only for
 * the unresolved fallback, so the version is not needed.
 *
 * @param groupId    group id (after property interpolation; may still contain {@code ${...}})
 * @param artifactId artifact id
 * @param scope      declared scope, {@link Scope#COMPILE} when absent or unknown
 * @param type       declared type, {@code jar} by default
 * @param classifier optional classifier
 */
public record DeclaredDependency(String groupId, String artifactId, Scope scope, String type,
                                 Optional<String> classifier) {

    public DeclaredDependency {
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        scope = scope == null ? Scope.COMPILE : scope;
        type = type == null || type.isBlank() ? Gav.JAR : type.strip();
        classifier = classifier == null ? Optional.empty() : classifier.filter(c -> !c.isBlank());
    }

    /** {@code groupId:artifactId}. */
    public String coordinates() {
        return groupId + ":" + artifactId;
    }

    /** True if the artifact is a jar-like file that can be put on a classpath. */
    public boolean isJar() {
        return Gav.JAR.equals(type) || "test-jar".equals(type);
    }

    /** True if the group or artifact id still contains an uninterpolated {@code ${...}} expression. */
    public boolean hasUnresolvedProperty() {
        return groupId.contains("${") || artifactId.contains("${") || groupId.isBlank() || artifactId.isBlank();
    }
}
