package io.github.vecluu.jdoc.index;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.ParameterInfo;
import io.github.vecluu.jdoc.model.SourceRange;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Inserts a JDK-sized artifact (~20k types, ~200k members) and checks that writes and scoped reads stay fast. */
@Tag("slow")
class BulkInsertIT {

    private static final int TYPES = 20_000;
    private static final int MEMBERS_PER_TYPE = 10;

    @TempDir
    Path tmp;

    @Test
    void insertsJdkSizedArtifactQuickly() {
        try (IndexDatabase database = IndexDatabase.open(tmp.resolve("index.db"), Duration.ofSeconds(5))) {
            ArtifactRepository artifacts = new JdbcArtifactRepository(database);
            TypeRepository types = new JdbcTypeRepository(database);
            MemberRepository members = new JdbcMemberRepository(database);
            long other = artifacts.save(IndexFixtures.artifact("g:other:1")).id();
            types.insertAll(other, List.of(IndexFixtures.type("pkg0.Type0")));

            long start = System.nanoTime();
            long jdk = database.inTransaction(() -> {
                long id = artifacts.save(IndexFixtures.artifact("g:jdk-like:1")).id();
                List<IndexedType> stored = types.insertAll(id, typeDescriptors());
                Map<Long, List<MemberDescriptor>> byType = new LinkedHashMap<>();
                for (IndexedType type : stored) {
                    byType.put(type.id(), memberDescriptors(type.name()));
                }
                assertThat(members.insertAll(byType)).isEqualTo(TYPES * MEMBERS_PER_TYPE);
                return id;
            });
            Duration insert = Duration.ofNanos(System.nanoTime() - start);
            assertThat(insert).as("bulk insert time").isLessThan(Duration.ofSeconds(30));

            List<Long> scope = List.of(other, jdk);
            start = System.nanoTime();
            for (int i = 0; i < 20; i++) {
                assertThat(types.findBySimpleName("type" + (i * 997), scope)).hasSize(i == 0 ? 2 : 1);
                assertThat(members.findByName("method" + (i % MEMBERS_PER_TYPE), List.of(jdk))).hasSize(TYPES);
                assertThat(types.findByBinaryName(TypeName.of("pkg" + i + ".Type" + i), scope)).isNotEmpty();
                assertThat(members.searchByName("ethod" + (i % MEMBERS_PER_TYPE), scope, 50)).hasSize(50);
            }
            Duration queries = Duration.ofNanos(System.nanoTime() - start);
            assertThat(queries).as("80 scoped queries").isLessThan(Duration.ofSeconds(20));

            IndexedType sample = types.findBySimpleName("Type12345", scope).getFirst();
            assertThat(members.membersOf(sample.id())).hasSize(MEMBERS_PER_TYPE);
            assertThat(types.listNames(scope)).hasSize(TYPES + 1);

            start = System.nanoTime();
            assertThat(artifacts.delete(jdk)).isTrue();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).as("cascade delete").isLessThan(Duration.ofSeconds(30));
            assertThat(types.listNames(scope)).hasSize(1);
        }
    }

    private static List<TypeDescriptor> typeDescriptors() {
        List<TypeDescriptor> result = new ArrayList<>(TYPES);
        for (int i = 0; i < TYPES; i++) {
            TypeName name = TypeName.of("pkg" + (i % 200) + ".Type" + i);
            result.add(TypeDescriptor.builder()
                    .name(name)
                    .kind(TypeKind.CLASS)
                    .visibility(Visibility.PUBLIC)
                    .modifiers("public")
                    .supertypes(List.of(TypeName.of("java.lang.Object"), TypeName.of("java.io.Serializable")))
                    .firstSentence("Synthetic type number " + i + ".")
                    .docSource(DocSource.JDK)
                    .entryPath(name.sourceEntryPath())
                    .declarationRange(Optional.of(new SourceRange(0, 100)))
                    .build());
        }
        return result;
    }

    private static List<MemberDescriptor> memberDescriptors(TypeName type) {
        List<MemberDescriptor> result = new ArrayList<>(MEMBERS_PER_TYPE);
        for (int m = 0; m < MEMBERS_PER_TYPE; m++) {
            result.add(MemberDescriptor.builder()
                    .declaringType(type)
                    .name("method" + m)
                    .kind(MemberKind.METHOD)
                    .signature("List<String> method" + m + "(Map<String, Integer> map, int count)")
                    .parameters(List.of(new ParameterInfo("map", "Map<String, Integer>", "Map"),
                            new ParameterInfo("count", "int", "int")))
                    .visibility(Visibility.PUBLIC)
                    .firstSentence("Does thing " + m + ".")
                    .range(Optional.of(new SourceRange(m * 100, m * 100 + 90)))
                    .docRange(Optional.of(new SourceRange(m * 100, m * 100 + 20)))
                    .build());
        }
        return result;
    }
}
