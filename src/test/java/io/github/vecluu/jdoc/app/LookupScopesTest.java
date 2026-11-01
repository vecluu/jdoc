package io.github.vecluu.jdoc.app;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.indexer.IndexedClasspath;
import io.github.vecluu.jdoc.lookup.LookupScope;
import io.github.vecluu.jdoc.lookup.ScopedArtifact;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolutionStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LookupScopesTest {

    private static final ArtifactKey A = ArtifactKey.maven(Gav.of("g", "a", "1"));
    private static final ArtifactKey B = ArtifactKey.maven(Gav.of("g", "b", "1"));
    private static final ArtifactKey JDK = ArtifactKey.jdk(25);

    @Test
    void putsTheJdkFirstThenTheClasspathInOrder() {
        Map<Long, ArtifactKey> keys = new LinkedHashMap<>();
        keys.put(7L, B);
        keys.put(3L, A);
        keys.put(9L, JDK);

        LookupScope scope = LookupScopes.from(new IndexedClasspath(List.of(7L, 3L, 9L), keys, Optional.of(9L),
                ResolutionStatus.STALE, List.of()));

        assertThat(scope.artifacts()).containsExactly(new ScopedArtifact(9, JDK), new ScopedArtifact(7, B),
                new ScopedArtifact(3, A));
        assertThat(scope.status()).isEqualTo(ResolutionStatus.STALE);
    }

    @Test
    void worksWithoutAJdk() {
        LookupScope scope = LookupScopes.from(new IndexedClasspath(List.of(3L), Map.of(3L, A), Optional.empty(),
                ResolutionStatus.UNRESOLVED, List.of()));

        assertThat(scope.artifactIds()).containsExactly(3L);
        assertThat(scope.versionUnresolved()).isTrue();
    }
}
