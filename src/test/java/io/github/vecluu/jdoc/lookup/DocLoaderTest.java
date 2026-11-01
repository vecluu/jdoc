package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.lookup.LookupIndex.method;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.indexer.MemberEntry;
import io.github.vecluu.jdoc.indexer.TypeEntry;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocLoaderTest {

    private static final TypeName FROM_JAVADOC = TypeName.of("j.FromJavadoc");

    @TempDir
    Path tmp;

    private LookupIndex index;
    private LookupServices services;

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        SampleSources.addTo(index);
        TypeDescriptor fromJavadoc = LookupIndex.publicClass(FROM_JAVADOC.binaryName(), "a.Base").toBuilder()
                .docSource(DocSource.JAVADOC)
                .entryPath(FROM_JAVADOC.javadocPagePath())
                .build();
        index.add(LookupIndex.artifact(ArtifactKey.maven(Gav.parse("com.acme:jd:1.0"))).toBuilder()
                        .javadocJar(Optional.of(tmp.resolve("jd-1.0-javadoc.jar")))
                        .build(),
                List.of(new TypeEntry(fromJavadoc, doc("A type documented only in HTML."), List.of(
                                new MemberEntry(method("j.FromJavadoc", "run"), doc("Runs it.")),
                                MemberEntry.undocumented(method("j.FromJavadoc", "name")))),
                        // a decompiled type never has docs; one stored by mistake is ignored
                        new TypeEntry(LookupIndex.publicClass("j.Decompiled", "a.Base"), doc("Ignored."),
                                List.of(new MemberEntry(method("j.Decompiled", "name"), doc("Ignored."))))),
                Map.of("j", doc("The j package.")));
        services = index.services((gav, jar, type) -> {
            throw new AssertionError("no decompiling for docs");
        });
    }

    @AfterEach
    void tearDown() {
        services.close();
        index.close();
    }

    @Test
    void readsStoredTypeDocsOfSourcesJdkJavadocAndNoneForDecompiled() {
        assertThat(typeDoc("a.Base").description())
                .isEqualTo("Base class for named things. Second sentence.\n\nSecond paragraph.");
        assertThat(typeDoc("java.lang.Object").firstSentence())
                .isEqualTo("Class `Object` is the root of the class hierarchy.");
        assertThat(typeDoc("j.FromJavadoc").description()).isEqualTo("A type documented only in HTML.");
        assertThat(typeDoc("j.Decompiled")).isEqualTo(RenderedDoc.missing());
    }

    @Test
    void loadsOwnMemberDocsFromSource() {
        MemberDoc doc = memberDoc("a.Base#size(int)");

        assertThat(doc.doc().description()).isEqualTo("Sizes it.");
        assertThat(doc.doc().blockTags()).containsExactly("@param scale the scale", "@return the size");
        assertThat(doc.inheritedFrom()).isEmpty();
    }

    @Test
    void undocumentedOverrideTakesTheSuperclassDoc() {
        MemberDoc doc = memberDoc("a.Impl#size(int)");

        assertThat(doc.doc().description()).isEqualTo("Sizes it.");
        assertThat(doc.doc().blockTags()).containsExactly("@param scale the scale", "@return the size");
        assertThat(doc.inheritedFrom()).contains(TypeName.of("a.Base"));
    }

    @Test
    void inheritDocIsResolvedThroughAnInterface() {
        MemberDoc doc = memberDoc("a.Base#name");

        assertThat(doc.doc().description()).isEqualTo("Returns the name.\n\nBase adds this.");
        assertThat(doc.doc().firstSentence()).isEqualTo("Returns the name.");
        assertThat(doc.doc().blockTags()).containsExactly("@return the name, never empty");
        assertThat(doc.doc().inheritsDoc()).isFalse();
        assertThat(doc.inheritedFrom()).contains(TypeName.of("a.Named"));

        MemberDoc other = memberDoc("a.Other#name");
        assertThat(other.doc().description()).isEqualTo("Returns the name.");
        assertThat(other.inheritedFrom()).contains(TypeName.of("a.Named"));
    }

    @Test
    void inheritanceReachesJdkSourcesAndTypeVariableParameters() {
        MemberDoc toString = memberDoc("a.Impl#toString");
        assertThat(toString.doc().description()).isEqualTo("Returns a string representation of the object.");
        assertThat(toString.inheritedFrom()).contains(TypeName.of("java.lang.Object"));

        RenderedDoc compareTo = memberDoc("a.Named#compareTo").doc();
        assertThat(compareTo.description()).isEqualTo("Compares by name.");
    }

    @Test
    void javadocTypesUseTheirPageAndStillInheritMissingDocs() {
        assertThat(memberDoc("j.FromJavadoc#run").doc().description()).isEqualTo("Runs it.");

        MemberDoc name = memberDoc("j.FromJavadoc#name");
        assertThat(name.doc().description()).isEqualTo("Returns the name.\n\nBase adds this.");
        assertThat(name.inheritedFrom()).contains(TypeName.of("a.Base"));
    }

    @Test
    void decompiledTypesHaveNoDocsAtAll() {
        MemberDoc doc = memberDoc("j.Decompiled#name");

        assertThat(doc.doc()).isEqualTo(RenderedDoc.missing());
        assertThat(doc.inheritedFrom()).isEmpty();
    }

    @Test
    void fieldsConstructorsAndImplicitMembersDoNotInherit() {
        assertThat(memberDoc("a.Impl#LIMIT").doc()).isEqualTo(RenderedDoc.missing());
        assertThat(memberDoc("a.Impl#Impl").doc().description()).isEqualTo("Creates an impl.");
        assertThat(memberDoc("a.Point#x").doc().description()).isNotBlank();
    }

    @Test
    void packageDocComesFromPackageInfoOrTheJavadocJar() {
        LookupScope scope = index.scope();
        List<LocatedType> packageA = services.types().locate(index.types.findByPackage("a", scope.artifactIds()),
                scope);
        List<LocatedType> packageJ = services.types().locate(index.types.findByPackage("j", scope.artifactIds()),
                scope);
        List<LocatedType> javaLang = services.types().locate(
                index.types.findByPackage("java.lang", scope.artifactIds()), scope);

        assertThat(services.docs().packageDoc("a", packageA)).hasValueSatisfying(doc -> {
            assertThat(doc.doc().firstSentence()).isEqualTo("Named things and their implementations.");
            assertThat(doc.artifact().label()).isEqualTo("named 1.0");
        });
        assertThat(services.docs().packageDoc("j", packageJ))
                .hasValueSatisfying(doc -> assertThat(doc.doc().description()).isEqualTo("The j package."));
        assertThat(services.docs().packageDoc("java.lang", javaLang)).isEmpty();
    }

    @Test
    void docsAndInheritanceAcrossArtifactsComeFromTheIndexAloneWithoutAnyJar() throws IOException {
        try (var jars = Files.walk(tmp.resolve("jars"))) {
            jars.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
        assertThat(tmp.resolve("jars")).doesNotExist();

        MemberDoc toString = memberDoc("a.Impl#toString");
        assertThat(toString.doc().description()).isEqualTo("Returns a string representation of the object.");
        assertThat(toString.inheritedFrom()).contains(TypeName.of("java.lang.Object"));
        assertThat(found("a.Impl").artifact()).isNotEqualTo(found("java.lang.Object").artifact());
        MemberDoc name = memberDoc("j.FromJavadoc#name");
        assertThat(name.doc().description()).isEqualTo("Returns the name.\n\nBase adds this.");
        assertThat(typeDoc("a.Base").firstSentence()).isEqualTo("Base class for named things.");
    }

    private LocatedType found(String binaryName) {
        return services.types().first(TypeName.of(binaryName), index.scope()).orElseThrow();
    }

    private RenderedDoc typeDoc(String binaryName) {
        LocatedType type = services.types().first(TypeName.of(binaryName), index.scope()).orElseThrow();
        return services.docs().typeDoc(type);
    }

    private MemberDoc memberDoc(String reference) {
        MemberLookup.Found found = (MemberLookup.Found) services.memberLookup().lookup(reference, index.scope());
        return services.docs().memberDoc(found.members().getFirst(), index.scope());
    }

    private static RenderedDoc doc(String text) {
        return new RenderedDoc(text, text, List.of(), false);
    }
}
