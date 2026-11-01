package io.github.vecluu.jdoc.index;

import static io.github.vecluu.jdoc.index.IndexFixtures.artifact;
import static io.github.vecluu.jdoc.index.IndexFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeName;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcSupertypeRepositoryTest {

    private static final TypeName SHAPE = TypeName.of("p.Shape");
    private static final TypeName OBJECT = TypeName.of("java.lang.Object");

    @TempDir
    Path tmp;

    private IndexDatabase database;
    private TypeRepository types;
    private SupertypeRepository supertypes;
    private long libA;
    private long libB;

    @BeforeEach
    void open() {
        database = IndexDatabase.open(tmp.resolve("index.db"), Duration.ofSeconds(5));
        types = new JdbcTypeRepository(database);
        supertypes = new JdbcSupertypeRepository(database);
        ArtifactRepository artifacts = new JdbcArtifactRepository(database);
        libA = artifacts.save(artifact("g:a:1")).id();
        libB = artifacts.save(artifact("g:b:1")).id();
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void keepsSupertypeOrder() {
        TypeName comparable = TypeName.of("java.lang.Comparable");
        IndexedType circle = types.insertAll(libA, List.of(type("p.Circle", OBJECT, SHAPE, comparable))).getFirst();
        assertThat(supertypes.supertypesOf(circle.id())).containsExactly(OBJECT, SHAPE, comparable);
        assertThat(supertypes.supertypesOf(-1)).isEmpty();
    }

    @Test
    void findsDirectSubtypesInScopeWithTheirSupertypes() {
        types.insertAll(libA, List.of(type("p.Square", OBJECT, SHAPE), type("p.Circle", OBJECT, SHAPE),
                type("p.Unrelated", OBJECT)));
        types.insertAll(libB, List.of(type("q.Other", SHAPE)));
        List<IndexedType> subtypes = supertypes.directSubtypesOf(SHAPE, List.of(libA));
        assertThat(subtypes).extracting(t -> t.name().simpleName()).containsExactly("Circle", "Square");
        assertThat(subtypes.getFirst().descriptor().supertypes()).containsExactly(OBJECT, SHAPE);
        assertThat(supertypes.directSubtypesOf(SHAPE, List.of(libA, libB))).hasSize(3);
        assertThat(supertypes.directSubtypesOf(SHAPE, List.of())).isEmpty();
    }

    @Test
    void indirectSubtypesAreNotReturned() {
        types.insertAll(libA, List.of(type("p.Square", SHAPE), type("p.RoundedSquare", TypeName.of("p.Square"))));
        assertThat(supertypes.directSubtypesOf(SHAPE, List.of(libA)))
                .extracting(t -> t.name().simpleName()).containsExactly("Square");
    }
}
