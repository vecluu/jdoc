package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.testsupport.SampleLibrary.GENERATED_MODEL;
import static io.github.vecluu.jdoc.testsupport.SampleLibrary.KOTLIN_UTILS;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.decompile.DecompiledSourceCache;
import io.github.vecluu.jdoc.decompile.VineflowerDecompiler;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.testsupport.BuiltLibrary;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end lookups over the real sample library ({@link SampleLibrary#mixed()}: sources, javadoc-only and
 * decompiled types) indexed by the real jar indexer (docs stored in the index) plus a mini JDK {@code src.zip}, with
 * real Vineflower for {@code jdoc source}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LookupIT {

    private LookupIndex index;
    private LookupServices services;
    private LookupScope scope;
    private CachePaths cache;

    @BeforeAll
    void indexSampleLibrary(@TempDir Path tmp) {
        BuiltLibrary library = SampleLibrary.mixed().writeTo(tmp.resolve("repo"));
        index = LookupIndex.open(tmp);
        index.addSources(ArtifactKey.jdk(25), DocSource.JDK, "java.base/", SampleSources.jdk());
        SampleLibraryIndexer.index(index, library);
        cache = new CachePaths(tmp.resolve("cache"));
        services = index.services(new DecompiledSourceCache(cache, new VineflowerDecompiler()));
        scope = index.scope();
    }

    @AfterAll
    void close() {
        services.close();
        index.close();
    }

    @Test
    void resolvesTypesByEveryNameForm() {
        assertThat(found("com.example.sample.Shapes.Registry.Entry").name())
                .isEqualTo(SampleLibrary.SHAPES_REGISTRY_ENTRY);
        assertThat(found("Shapes$Registry$Entry").name()).isEqualTo(SampleLibrary.SHAPES_REGISTRY_ENTRY);
        assertThat(found("Entry").name()).isEqualTo(SampleLibrary.SHAPES_REGISTRY_ENTRY);
        assertThat(found("Square.Corner").name()).isEqualTo(SampleLibrary.SQUARE_CORNER);
        assertThat(found("Circle").label()).isEqualTo("sample-lib 1.0.0");
        assertThat(services.typeResolver().resolve("ShapeCache", scope)).isInstanceOf(TypeLookup.NotFound.class);
    }

    @Test
    void classViewOfASourcesType() {
        ClassView view = services.classView().view(found("Circle"), scope);

        assertThat(view.firstParagraph()).isEqualTo("A circle defined by its radius.");
        assertThat(view.members()).extracting(member -> member.descriptor().signature())
                .containsExactly("Circle(double radius)", "Circle(double radius, Color color)", "double radius()",
                        "double area()", "Circle scale(double factor)", "double diameter()");
        assertThat(view.inherited()).extracting(group -> group.declaringType().name().simpleName())
                .containsExactly("AbstractShape", "Shape", "Object");
        assertThat(view.inherited().get(0).names()).containsExactly("color", "name");
        assertThat(view.inherited().get(1).names()).containsExactly("DEFAULT_UNIT", "describe", "compareTo");
        assertThat(view.inherited().get(2).names()).containsExactly("equals", "getClass", "hashCode", "toString");
    }

    @Test
    void classViewOfJavadocAndDecompiledTypes() {
        LocatedType generated = found("GeneratedModel");
        assertThat(generated.descriptor().docSource()).isEqualTo(DocSource.JAVADOC);
        assertThat(services.classView().view(generated, scope).firstParagraph())
                .isEqualTo("A generated model class.");

        LocatedType kotlin = found("KotlinUtils");
        assertThat(kotlin.descriptor().docSource()).isEqualTo(DocSource.DECOMPILED);
        ClassView kotlinView = services.classView().view(kotlin, scope);
        assertThat(kotlinView.firstParagraph()).isEmpty();
        assertThat(kotlinView.members()).extracting(member -> member.descriptor().name())
                .containsExactly("joinNames", "twice");
    }

    @Test
    void methodDocsResolveInheritDocAndUndocumentedOverrides() {
        RenderedDoc area = methodDoc("Circle#area").doc();
        assertThat(area.description()).isEqualTo("Returns the area of this shape.");
        assertThat(area.blockTags()).containsExactly("@return the area, never negative");

        MemberDoc scale = methodDoc("Circle#scale(double)");
        assertThat(scale.doc().firstSentence()).isEqualTo("Scales this shape by a factor.");
        assertThat(scale.inheritedFrom()).contains(SampleLibrary.SHAPE);

        MemberDoc name = methodDoc("Square#name");
        assertThat(name.doc().description())
                .isEqualTo("Returns the name of this shape.\n\nThis implementation returns the simple class name.");

        assertThat(methodDoc("GeneratedModel#withId(String)").doc().blockTags())
                .containsExactly("@param id the new id", "@return this model");
        assertThat(methodDoc("KotlinUtils#twice").doc()).isEqualTo(RenderedDoc.missing());
    }

    @Test
    void methodLookupListsOverloadsAndMatchesTypeVariables() {
        assertThat(lookup("Shapes#max").members()).hasSize(3);
        assertThat(lookup("Shapes#max(Comparable,Comparable)").members()).hasSize(1);
        assertThat(lookup("Shapes#max(T,T)").members()).hasSize(1);
        assertThat(lookup("Box#put(Shape)").members()).extracting(MemberMatch::reference)
                .containsExactly("com.example.sample.Box#put(Shape)");
    }

    @Test
    void sourceFromSourcesJarAndThroughRealVineflower() {
        MemberSource.Available radius = available("Circle#radius");
        assertThat(radius.decompiled()).isFalse();
        assertThat(radius.code()).startsWith("/**").contains("return radius;");

        MemberSource.Available twice = available("KotlinUtils#twice");
        assertThat(twice.decompiled()).isTrue();
        assertThat(twice.code()).contains("int twice(int value)").contains("value * 2");
        assertThat(twice.doc()).isEmpty();
        assertThat(cache.decompiledSource(SampleLibrary.GAV, KOTLIN_UTILS)).exists();

        MemberSource.Available withId = available("GeneratedModel#withId");
        assertThat(withId.decompiled()).isTrue();
        assertThat(withId.code()).contains("withId(String id)");
        assertThat(withId.doc()).hasValueSatisfying(doc -> assertThat(doc.firstSentence())
                .isEqualTo("Sets the model id."));
        assertThat(cache.decompiledSource(SampleLibrary.GAV, GENERATED_MODEL)).exists();
    }

    @Test
    void implsFindAndMemberSearch() {
        assertThat(services.hierarchy().subtypes(found("Shape"), scope))
                .extracting(subtype -> subtype.type().name().simpleName())
                .containsExactly("AbstractShape", "Circle", "Square");

        FindResult humps = services.find().find("AS", scope);
        assertThat(humps.hits()).extracting(FindHit::reference).contains("com.example.sample.AbstractShape");
        FindResult largest = services.find().find("larg", scope);
        assertThat(largest.hits()).extracting(FindHit::reference)
                .containsExactly("com.example.sample.Shapes#largest(Shape[])");

        assertThat(services.memberSearch().search("name", scope)).extracting(MemberMatch::reference)
                .containsExactly("com.example.sample.AbstractShape#name()", "com.example.sample.Shape#name()",
                        "com.example.sample.Shapes.Registry.Entry#name()");
    }

    @Test
    void packageViewWithPackageInfo() {
        PackageLookup lookup = services.packages().describe("com.example.sample", scope);

        assertThat(lookup).isInstanceOfSatisfying(PackageLookup.Found.class, found -> {
            assertThat(found.view().doc()).hasValueSatisfying(doc -> assertThat(doc.doc().firstSentence())
                    .isEqualTo("Sample shapes library used by jdoc tests."));
            assertThat(found.view().types()).extracting(type -> type.name().relativeName())
                    .contains("Circle", "Shapes.Registry.Entry", "Square.Corner")
                    .doesNotContain("ShapeCache");
        });
        assertThat(services.packages().describe("com.example.sample.gen", scope))
                .isInstanceOfSatisfying(PackageLookup.Found.class, found -> assertThat(found.view().types())
                        .extracting(type -> type.name()).containsExactly(GENERATED_MODEL));
    }

    private LocatedType found(String input) {
        TypeLookup lookup = services.typeResolver().resolve(input, scope);
        assertThat(lookup).as(input).isInstanceOf(TypeLookup.Found.class);
        return ((TypeLookup.Found) lookup).type();
    }

    private MemberLookup.Found lookup(String input) {
        MemberLookup lookup = services.memberLookup().lookup(input, scope);
        assertThat(lookup).as(input).isInstanceOf(MemberLookup.Found.class);
        return (MemberLookup.Found) lookup;
    }

    private MemberDoc methodDoc(String input) {
        return services.docs().memberDoc(lookup(input).members().getFirst(), scope);
    }

    private MemberSource.Available available(String input) {
        MemberSource source = services.sources().source(lookup(input).members().getFirst(), scope);
        assertThat(source).as(input).isInstanceOf(MemberSource.Available.class);
        return (MemberSource.Available) source;
    }
}
