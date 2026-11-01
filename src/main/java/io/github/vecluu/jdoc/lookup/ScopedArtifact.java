package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.model.ArtifactKey;
import java.util.Objects;

/**
 * One indexed artifact of a {@link LookupScope}.
 *
 * @param id  database id of the artifact
 * @param key the artifact's identity
 */
public record ScopedArtifact(long id, ArtifactKey key) {

    public ScopedArtifact {
        Objects.requireNonNull(key, "key");
    }

    /** User-facing label, {@code commons-lang3 3.17.0} or {@code jdk 25}. */
    public String label() {
        return key.label();
    }
}
