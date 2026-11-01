package io.github.vecluu.jdoc.index;

import static io.github.vecluu.jdoc.index.IndexFixtures.artifact;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcArtifactRepositoryTest {

    @TempDir
    Path tmp;

    private IndexDatabase database;
    private ArtifactRepository artifacts;

    @BeforeEach
    void open() {
        database = IndexDatabase.open(tmp.resolve("index.db"), Duration.ofSeconds(5));
        artifacts = new JdbcArtifactRepository(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void roundTripsEveryField() {
        IndexedArtifact original = artifact("org.example:lib:1.2.3").toBuilder()
                .sourcesJar(Optional.of(Path.of("/repo/lib-1.2.3-sources.jar")))
                .javadocJar(Optional.of(Path.of("/repo/lib-1.2.3-javadoc.jar")))
                .build();
        IndexedArtifact saved = artifacts.save(original);
        assertThat(saved.id()).isPositive();
        assertThat(artifacts.findByKey(original.key())).contains(saved);
        assertThat(saved).isEqualTo(original.toBuilder().id(saved.id()).build());
    }

    @Test
    void roundTripsJdkKeyAndAbsentJars() {
        IndexedArtifact jdk = artifact("g:a:1").toBuilder().key(ArtifactKey.jdk(25))
                .jarPath(Path.of("/jdk/lib/src.zip")).build();
        IndexedArtifact saved = artifacts.save(jdk);
        IndexedArtifact found = artifacts.findByKey(ArtifactKey.jdk(25)).orElseThrow();
        assertThat(found).isEqualTo(saved);
        assertThat(found.sourcesJar()).isEmpty();
        assertThat(found.javadocJar()).isEmpty();
    }

    @Test
    void roundTripsClassifiedGav() {
        Gav gav = Gav.parse("g:a:jar:tests:1.0");
        IndexedArtifact saved = artifacts.save(artifact("g:a:1").toBuilder().key(ArtifactKey.maven(gav)).build());
        assertThat(artifacts.findByKey(ArtifactKey.maven(gav))).contains(saved);
        assertThat(artifacts.findByKey(ArtifactKey.maven(Gav.parse("g:a:1.0")))).isEmpty();
    }

    @Test
    void findsByJarPathAndIds() {
        IndexedArtifact a = artifacts.save(artifact("g:a:1"));
        IndexedArtifact b = artifacts.save(artifact("g:b:1"));
        assertThat(artifacts.findByJarPath(a.jarPath())).containsExactly(a);
        assertThat(artifacts.findByJarPath(Path.of("/nowhere.jar"))).isEmpty();
        assertThat(artifacts.findByIds(List.of(b.id(), a.id(), 999L))).containsExactly(a, b);
        assertThat(artifacts.findByIds(List.of())).isEmpty();
    }

    @Test
    void listsAllByKey() {
        IndexedArtifact b = artifacts.save(artifact("g:b:1"));
        IndexedArtifact a = artifacts.save(artifact("g:a:1"));
        assertThat(artifacts.findAll()).containsExactly(a, b);
    }

    @Test
    void saveReplacesSameKeyAndCascades() {
        IndexedArtifact first = artifacts.save(artifact("g:a:1"));
        TypeRepository types = new JdbcTypeRepository(database);
        types.insertAll(first.id(), List.of(IndexFixtures.type("p.A")));
        IndexedArtifact second = artifacts.save(artifact("g:a:1").toBuilder().jarSize(1).build());
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(artifacts.findAll()).containsExactly(second);
        assertThat(types.countByArtifact(first.id())).isZero();
    }

    @Test
    void deleteCascadesToTypesSupertypesAndMembers() {
        IndexedArtifact saved = artifacts.save(artifact("g:a:1"));
        IndexedArtifact other = artifacts.save(artifact("g:b:1"));
        TypeRepository types = new JdbcTypeRepository(database);
        MemberRepository members = new JdbcMemberRepository(database);
        SupertypeRepository supertypes = new JdbcSupertypeRepository(database);
        long typeId = types.insertAll(saved.id(), List.of(IndexFixtures.type("p.A",
                TypeName.of("p.Base")))).getFirst().id();
        members.insertAll(typeId, List.of(IndexFixtures.method("p.A", "run")));
        long otherType = types.insertAll(other.id(), List.of(IndexFixtures.type("p.B"))).getFirst().id();
        members.insertAll(otherType, List.of(IndexFixtures.method("p.B", "run")));

        assertThat(artifacts.delete(saved.id())).isTrue();
        assertThat(artifacts.delete(saved.id())).isFalse();
        assertThat(types.findById(typeId)).isEmpty();
        assertThat(members.membersOf(typeId)).isEmpty();
        assertThat(supertypes.supertypesOf(typeId)).isEmpty();
        assertThat(members.membersOf(otherType)).hasSize(1);
    }
}
