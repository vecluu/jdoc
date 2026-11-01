package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.testsupport.BuiltLibrary;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestLibrary;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Indexes the mixed sample library (sources, javadoc and bytecode-only types) into a real SQLite file. */
class ArtifactIndexerIT {

    @TempDir
    static Path tmp;

    private static IndexerFixture fixture;
    private static StoredArtifact stored;
    private static long artifactId;

    @BeforeAll
    static void index() {
        BuiltLibrary library = SampleLibrary.mixed().writeTo(tmp.resolve("repo"));
        fixture = new IndexerFixture(tmp.resolve("index.db"));
        stored = fixture.artifactIndexer().index(library.gav(), library.binaryJar(), new FileStamp(1, 2));
        artifactId = stored.artifact().id();
    }

    @AfterAll
    static void close() {
        fixture.close();
    }

    private static TypeDescriptor type(TypeName name) {
        return fixture.type(name, artifactId).descriptor();
    }

    private static Map<String, MemberDescriptor> members(TypeName name) {
        return fixture.members(name, artifactId).stream().map(IndexedMember::descriptor)
                .collect(Collectors.toMap(MemberDescriptor::erasedKey, Function.identity()));
    }

    @Test
    void storesTheArtifactWithItsDocJars() {
        assertThat(stored.artifact().sourcesJar()).isPresent();
        assertThat(stored.artifact().javadocJar()).isPresent();
        assertThat(stored.artifact().jarMtime()).isEqualTo(1);
        assertThat(stored.artifact().jarSize()).isEqualTo(2);
        assertThat(fixture.types.countByArtifact(artifactId)).isEqualTo(stored.typeCount());
    }

    @Test
    void everyTypeGetsTheRightDocSource() {
        Map<TypeName, DocSource> docSources = fixture.types.listNames(List.of(artifactId)).stream()
                .collect(Collectors.toMap(ref -> ref.name(), ref -> type(ref.name()).docSource()));

        assertThat(docSources.get(SampleLibrary.GENERATED_MODEL)).isEqualTo(DocSource.JAVADOC);
        assertThat(docSources.get(SampleLibrary.KOTLIN_UTILS)).isEqualTo(DocSource.DECOMPILED);
        assertThat(docSources).containsKeys(SampleLibrary.API_TYPES.toArray(TypeName[]::new));
        assertThat(docSources).containsKey(SampleLibrary.SHAPE_CACHE);
        assertThat(docSources).doesNotContainKey(TypeName.of("com.example.sample.Color$1"));
        docSources.forEach((name, source) -> {
            if (!name.equals(SampleLibrary.GENERATED_MODEL) && !name.equals(SampleLibrary.KOTLIN_UTILS)) {
                assertThat(source).as(name.toString()).isEqualTo(DocSource.SOURCES);
            }
        });
    }

    @Test
    void sourceTypesHaveSourceDocsAndExactSupertypes() {
        TypeDescriptor circle = type(SampleLibrary.CIRCLE);
        assertThat(circle.entryPath()).isEqualTo("com/example/sample/Circle.java");
        assertThat(circle.firstSentence()).isEqualTo("A circle defined by its radius.");
        assertThat(circle.supertypes()).containsExactly(SampleLibrary.ABSTRACT_SHAPE);
        assertThat(circle.declarationRange()).isPresent();
        assertThat(members(SampleLibrary.CIRCLE).get("Circle(double)").firstSentence())
                .isEqualTo("Creates a red circle.");
        assertThat(members(SampleLibrary.CIRCLE).get("Circle(double)").range()).isPresent();

        TypeDescriptor entry = type(SampleLibrary.SHAPES_REGISTRY_ENTRY);
        assertThat(entry.entryPath()).isEqualTo("com/example/sample/Shapes.java");
        assertThat(entry.supertypes()).containsExactly(TypeName.of("java.lang.Record"));
    }

    @Test
    void recordsListTheirObjectMethodsFromSource() {
        Map<String, MemberDescriptor> point = members(SampleLibrary.POINT);

        assertThat(point).containsKeys("equals(Object)", "hashCode()", "toString()", "x()", "y()");
        assertThat(point.get("equals(Object)").signature()).isEqualTo("boolean equals(Object o)");
    }

    @Test
    void javadocTypesHaveBytecodeSignaturesAndHtmlFirstSentences() {
        TypeDescriptor generated = type(SampleLibrary.GENERATED_MODEL);
        assertThat(generated.entryPath()).isEqualTo("com/example/sample/gen/GeneratedModel.html");
        assertThat(generated.firstSentence()).isEqualTo("A generated model class.");
        Map<String, MemberDescriptor> members = members(SampleLibrary.GENERATED_MODEL);
        assertThat(members.get("withId(String)").signature()).isEqualTo("GeneratedModel withId(String arg0)");
        assertThat(members.get("withId(String)").firstSentence()).isEqualTo("Sets the model id.");
        assertThat(members.get("GeneratedModel()").firstSentence()).isEqualTo("Creates a model with the default id.");
    }

    @Test
    void decompiledTypesHaveSignaturesOnly() {
        TypeDescriptor kotlin = type(SampleLibrary.KOTLIN_UTILS);
        assertThat(kotlin.entryPath()).isEqualTo("com/example/sample/kt/KotlinUtils.class");
        assertThat(kotlin.firstSentence()).isEmpty();
        assertThat(members(SampleLibrary.KOTLIN_UTILS)).containsOnlyKeys("joinNames(List,String)", "twice(int)");
        assertThat(members(SampleLibrary.KOTLIN_UTILS).values()).allSatisfy(
                member -> assertThat(member.firstSentence()).isEmpty());
    }

    @Test
    void onlyApiMembersAreIndexed() {
        List<IndexedType> all = fixture.types.findByIds(fixture.types.listNames(List.of(artifactId)).stream()
                .map(ref -> ref.typeId()).toList());
        assertThat(all).isNotEmpty();
        for (IndexedType type : all) {
            assertThat(fixture.members.membersOf(type.id())).allSatisfy(
                    member -> assertThat(member.descriptor().visibility().isApi()).isTrue());
        }
        assertThat(type(SampleLibrary.SHAPE_CACHE).visibility().isApi()).isFalse();
        assertThat(members(SampleLibrary.SHAPE_CACHE)).containsOnlyKeys("size()");
    }

    @Test
    void wildcardImportedSupertypeFromAnotherArtifactIsExact(@TempDir Path dir) {
        BuiltLibrary base = TestLibrary.builder(Gav.of("com.a", "base", "1.0"))
                .source("com/a/Base.java", "package com.a; /** Base. */ public class Base {}")
                .source("com/c/Decoy.java", "package com.c; /** Decoy. */ public class Decoy {}")
                .withJavadoc(false)
                .build().writeTo(dir.resolve("base"));
        BuiltLibrary sub = TestLibrary.builder(Gav.of("com.b", "sub", "1.0"))
                .source("com/b/Sub.java", """
                        package com.b;
                        import com.c.*;
                        import com.a.*;
                        /** Sub. */
                        public class Sub extends Base implements Runnable {
                            public void run() {}
                        }
                        """)
                .classpath(base.binaryJar())
                .withJavadoc(false)
                .build().writeTo(dir.resolve("sub"));

        long subId = fixture.artifactIndexer().index(sub.gav(), sub.binaryJar(), new FileStamp(1, 1))
                .artifact().id();

        TypeDescriptor indexed = fixture.type(TypeName.of("com.b.Sub"), subId).descriptor();
        assertThat(indexed.docSource()).isEqualTo(DocSource.SOURCES);
        assertThat(indexed.firstSentence()).isEqualTo("Sub.");
        assertThat(indexed.supertypes()).containsExactly(TypeName.of("com.a.Base"), TypeName.of("java.lang.Runnable"));
    }

    @Test
    void reindexingReplacesTheArtifact(@TempDir Path dir) {
        BuiltLibrary library = SampleLibrary.binaryOnly().writeTo(dir);
        try (IndexerFixture own = new IndexerFixture(dir.resolve("own.db"))) {
            StoredArtifact first = own.artifactIndexer().index(library.gav(), library.binaryJar(), new FileStamp(1, 1));
            StoredArtifact second = own.artifactIndexer().index(library.gav(), library.binaryJar(),
                    new FileStamp(2, 2));

            assertThat(own.artifacts.findAll()).containsExactly(second.artifact());
            assertThat(own.types.countByArtifact(first.artifact().id())).isZero();
            assertThat(own.types.countByArtifact(second.artifact().id())).isEqualTo(first.typeCount());
        }
    }
}
