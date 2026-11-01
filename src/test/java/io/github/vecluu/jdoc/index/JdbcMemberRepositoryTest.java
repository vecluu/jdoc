package io.github.vecluu.jdoc.index;

import static io.github.vecluu.jdoc.index.IndexFixtures.artifact;
import static io.github.vecluu.jdoc.index.IndexFixtures.field;
import static io.github.vecluu.jdoc.index.IndexFixtures.method;
import static io.github.vecluu.jdoc.index.IndexFixtures.type;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.SourceRange;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcMemberRepositoryTest {

    @TempDir
    Path tmp;

    private IndexDatabase database;
    private MemberRepository members;
    private long libA;
    private long libB;
    private long typeA;
    private long typeB;

    @BeforeEach
    void open() {
        database = IndexDatabase.open(tmp.resolve("index.db"), Duration.ofSeconds(5));
        members = new JdbcMemberRepository(database);
        ArtifactRepository artifacts = new JdbcArtifactRepository(database);
        TypeRepository types = new JdbcTypeRepository(database);
        libA = artifacts.save(artifact("g:a:1")).id();
        libB = artifacts.save(artifact("g:b:1")).id();
        typeA = types.insertAll(libA, List.of(type("p.Alpha"))).getFirst().id();
        typeB = types.insertAll(libB, List.of(type("q.Beta"))).getFirst().id();
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    void roundTripsEveryField() {
        MemberDescriptor full = MemberDescriptor.builder()
                .declaringType(TypeName.of("p.Alpha"))
                .name("of")
                .kind(MemberKind.METHOD)
                .signature("static <T> Optional<T> of(Map<String, T> values, String... names)")
                .parameters(List.of(new ParameterInfo("values", "Map<String, T>", "Map"),
                        new ParameterInfo("names", "String...", "String[]")))
                .visibility(Visibility.PROTECTED)
                .isStatic(true)
                .deprecated(true)
                .firstSentence("Creates one.")
                .range(Optional.of(new SourceRange(5, 90)))
                .docRange(Optional.of(new SourceRange(5, 30)))
                .build();
        MemberDescriptor constructor = MemberDescriptor.builder()
                .declaringType(TypeName.of("p.Alpha"))
                .name("Alpha")
                .kind(MemberKind.CONSTRUCTOR)
                .signature("Alpha()")
                .visibility(Visibility.PRIVATE)
                .build();
        MemberDescriptor constant = MemberDescriptor.builder()
                .declaringType(TypeName.of("p.Alpha"))
                .name("RED")
                .kind(MemberKind.ENUM_CONSTANT)
                .signature("RED")
                .visibility(Visibility.PUBLIC)
                .isStatic(true)
                .range(Optional.of(new SourceRange(0, 3)))
                .build();
        assertThat(members.insertAll(typeA, List.of(full, constructor, constant))).isEqualTo(3);

        List<IndexedMember> stored = members.membersOf(typeA);
        assertThat(stored).extracting(IndexedMember::descriptor).containsExactly(full, constructor, constant);
        assertThat(stored).allSatisfy(m -> assertThat(m.typeId()).isEqualTo(typeA));
        assertThat(stored).extracting(IndexedMember::id).doesNotHaveDuplicates();
    }

    @Test
    void keepsDeclarationOrder() {
        members.insertAll(typeA, List.of(method("p.Alpha", "zeta"), field("p.Alpha", "ALPHA"),
                method("p.Alpha", "mid")));
        assertThat(members.membersOf(typeA)).extracting(m -> m.descriptor().name())
                .containsExactly("zeta", "ALPHA", "mid");
    }

    @Test
    void insertsSeveralTypesAndGroupsReads() {
        Map<Long, List<MemberDescriptor>> batch = new LinkedHashMap<>();
        batch.put(typeA, List.of(method("p.Alpha", "a1"), method("p.Alpha", "a2")));
        batch.put(typeB, List.of(method("q.Beta", "b1")));
        assertThat(members.insertAll(batch)).isEqualTo(3);
        Map<Long, List<IndexedMember>> grouped = members.membersOf(List.of(typeA, typeB));
        assertThat(grouped.get(typeA)).extracting(m -> m.descriptor().name()).containsExactly("a1", "a2");
        assertThat(grouped.get(typeB)).extracting(m -> m.descriptor().name()).containsExactly("b1");
        assertThat(members.membersOf(List.<Long>of())).isEmpty();
    }

    @Test
    void findsByNameIgnoringCaseWithinScope() {
        members.insertAll(typeA, List.of(method("p.Alpha", "getValue"), method("p.Alpha", "other")));
        members.insertAll(typeB, List.of(method("q.Beta", "GETVALUE")));
        assertThat(members.findByName("getvalue", List.of(libA)))
                .extracting(m -> m.descriptor().reference()).containsExactly("p.Alpha#getValue()");
        assertThat(members.findByName("getValue", List.of(libA, libB)))
                .extracting(m -> m.descriptor().declaringType().simpleName()).containsExactly("Alpha", "Beta");
        assertThat(members.findByName("getValue", List.of())).isEmpty();
        assertThat(members.findByName("get", List.of(libA))).isEmpty();
    }

    @Test
    void searchesNameSubstringWithLimit() {
        members.insertAll(typeA, List.of(method("p.Alpha", "getValue"), method("p.Alpha", "setValue"),
                field("p.Alpha", "VALUE_MAX"), method("p.Alpha", "size")));
        members.insertAll(typeB, List.of(method("q.Beta", "value")));
        assertThat(members.searchByName("value", List.of(libA), 10))
                .extracting(m -> m.descriptor().name()).containsExactly("getValue", "setValue", "VALUE_MAX");
        assertThat(members.searchByName("value", List.of(libA), 2)).hasSize(2);
        assertThat(members.searchByName("_", List.of(libA), 10))
                .extracting(m -> m.descriptor().name()).containsExactly("VALUE_MAX");
        assertThat(members.searchByName("value", List.of(libA, libB), 10)).hasSize(4);
    }

    @Test
    void emptyInsertReturnsZero() {
        assertThat(members.insertAll(typeA, List.of())).isZero();
        assertThat(members.membersOf(typeA)).isEmpty();
    }
}
