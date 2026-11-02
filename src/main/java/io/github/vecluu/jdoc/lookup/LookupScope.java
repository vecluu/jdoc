package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.ResolutionStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * The artifacts every lookup is restricted to: the current module's classpath plus the JDK, in classpath order.
 *
 * <p>Classpath order decides between equal binary names in several artifacts (the first wins, as with javac).
 * {@link #status()} is the module's resolution status; {@link ResolutionStatus#UNRESOLVED} means versions were
 * guessed and results should be marked {@code [version unresolved]}.
 *
 * @param artifacts the artifacts in classpath order; ids must be unique
 * @param status    how the classpath was resolved
 */
public record LookupScope(List<ScopedArtifact> artifacts, ResolutionStatus status) {

    public LookupScope {
        artifacts = List.copyOf(artifacts);
        Objects.requireNonNull(status, "status");
        if (artifacts.stream().map(ScopedArtifact::id).distinct().count() != artifacts.size()) {
            throw new IllegalArgumentException("artifact ids in a scope must be unique: " + artifacts);
        }
    }

    /** A resolved scope. */
    public static LookupScope of(List<ScopedArtifact> artifacts) {
        return new LookupScope(artifacts, ResolutionStatus.RESOLVED);
    }

    /** Artifact ids in classpath order, for the index's scoped queries. */
    public List<Long> artifactIds() {
        return artifacts.stream().map(ScopedArtifact::id).toList();
    }

    /** The artifact with this id, if it is in scope. */
    public Optional<ScopedArtifact> artifact(long id) {
        return artifacts.stream().filter(artifact -> artifact.id() == id).findFirst();
    }

    /** Position of an artifact in classpath order; artifacts outside the scope sort last. */
    public int rank(long id) {
        for (int i = 0; i < artifacts.size(); i++) {
            if (artifacts.get(i).id() == id) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** True if the module's versions were guessed ({@link ResolutionStatus#UNRESOLVED}). */
    public boolean versionUnresolved() {
        return status == ResolutionStatus.UNRESOLVED;
    }

    /** Orders values by the classpath rank of their artifact id. */
    <T> Comparator<T> byRank(ToLongFunction<T> artifactId) {
        Map<Long, Integer> ranks = new HashMap<>();
        for (int i = 0; i < artifacts.size(); i++) {
            ranks.put(artifacts.get(i).id(), i);
        }
        return Comparator.comparingInt(value -> ranks.getOrDefault(artifactId.applyAsLong(value), Integer.MAX_VALUE));
    }
}
