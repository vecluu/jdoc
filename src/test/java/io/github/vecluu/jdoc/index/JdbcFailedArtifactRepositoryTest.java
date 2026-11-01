package io.github.vecluu.jdoc.index;

import static io.github.vecluu.jdoc.index.IndexFixtures.artifact;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcFailedArtifactRepositoryTest {

    private static final ArtifactKey KEY = ArtifactKey.maven(Gav.parse("g:a:jar:tests:1.0"));

    @TempDir
    Path tmp;

    private IndexDatabase database;
    private FailedArtifactRepository failures;

    @BeforeEach
    void open() {
        database = IndexDatabase.open(tmp.resolve("index.db"), Duration.ofSeconds(5));
        failures = new JdbcFailedArtifactRepository(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void roundTripsAFailure() {
        FailedArtifact failure = new FailedArtifact(KEY, Path.of("/repo/a-1.0-tests.jar"), 1234, 56, "bad zip");

        failures.save(failure);

        assertThat(failures.find(KEY)).contains(failure);
        assertThat(failures.find(ArtifactKey.maven(Gav.parse("g:a:1.0")))).isEmpty();
        assertThat(failures.find(ArtifactKey.jdk(25))).isEmpty();
    }

    @Test
    void saveReplacesTheEarlierFailureOfTheArtifact() {
        failures.save(new FailedArtifact(KEY, Path.of("/a.jar"), 1, 2, "first"));
        FailedArtifact second = new FailedArtifact(KEY, Path.of("/a.jar"), 3, 4, "second");

        failures.save(second);

        assertThat(failures.find(KEY)).contains(second);
    }

    @Test
    void deleteForgetsTheFailure() {
        failures.save(new FailedArtifact(KEY, Path.of("/a.jar"), 1, 2, "x"));

        assertThat(failures.delete(KEY)).isTrue();
        assertThat(failures.delete(KEY)).isFalse();
        assertThat(failures.find(KEY)).isEmpty();
    }

    @Test
    void failuresAreIndependentOfIndexedArtifacts() {
        JdbcArtifactRepository artifacts = new JdbcArtifactRepository(database);
        long id = artifacts.save(artifact("g:b:1")).id();
        ArtifactKey jdk = ArtifactKey.jdk(25);
        failures.save(new FailedArtifact(jdk, Path.of("/jdk/lib/src.zip"), 1, 2, "x"));

        artifacts.delete(id);

        assertThat(failures.find(jdk)).isPresent();
    }

    @Test
    void reasonIsOneLineAndNeverEmpty() {
        assertThat(new FailedArtifact(KEY, Path.of("/a.jar"), 1, 2, " cannot\n  read\tjar ").reason())
                .isEqualTo("cannot read jar");
        assertThat(new FailedArtifact(KEY, Path.of("/a.jar"), 1, 2, null).reason()).isEqualTo("unknown error");
    }

    @Test
    void matchesOnlyTheSameJarState() {
        FailedArtifact failure = new FailedArtifact(KEY, Path.of("/a.jar"), 10, 20, "x");

        assertThat(failure.matches(Path.of("/a.jar"), 10, 20)).isTrue();
        assertThat(failure.matches(Path.of("/a.jar"), 11, 20)).isFalse();
        assertThat(failure.matches(Path.of("/a.jar"), 10, 21)).isFalse();
        assertThat(failure.matches(Path.of("/b.jar"), 10, 20)).isFalse();
    }
}
