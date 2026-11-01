package io.github.vecluu.jdoc.indexer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.javadoc.JavadocJarReader;
import io.github.vecluu.jdoc.javadoc.JavadocPageParser;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.KnownTypes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TypeDocResolverTest {

    private static final String FOO_JAVA = """
            package p;
            import q.*;
            import r.*;
            /** Foo from source. */
            public class Foo extends Base {
                /** Runs. */
                public void run() {}
                void hidden() {}
                private void secret() {}
                /** Nested from source. */
                public static class Inner {}
            }
            """;

    private static JavaSourceParser parser;

    @BeforeAll
    static void openParser() {
        parser = new JavaSourceParser();
    }

    @AfterAll
    static void closeParser() {
        parser.close();
    }

    private static TypeDocResolver resolver(MapSourceArchive sources, MapJavadocArchive javadoc,
                                            List<ScannedClass> binary) {
        KnownTypes known = KnownTypes.of(binary.stream().map(c -> c.type().name()).toList());
        return new TypeDocResolver(
                Optional.ofNullable(sources).map(s -> new SourceFiles(s, parser, known, DocSource.SOURCES)),
                Optional.ofNullable(javadoc).map(j -> new JavadocJarReader(j, new JavadocPageParser())));
    }

    private static Map<TypeName, TypeEntry> resolve(TypeDocResolver resolver, List<ScannedClass> group) {
        return resolver.resolve(group.getFirst().type().name().topLevel(), group).stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.type().name(), e -> e));
    }

    private static List<String> signatures(TypeEntry entry) {
        return entry.memberDescriptors().stream().map(MemberDescriptor::signature).toList();
    }

    @Test
    void sourceFileWinsAndSupertypesComeFromBytecode() {
        TypeName exactBase = TypeName.of("r.Base");
        List<ScannedClass> group = List.of(Scanned.type("p.Foo", List.of(exactBase), "run"),
                Scanned.type("p.Foo$Inner"));
        MapSourceArchive sources = new MapSourceArchive().with("p/Foo.java", FOO_JAVA);
        MapJavadocArchive javadoc = new MapJavadocArchive()
                .withTypePage("p/Foo.html", "Foo from javadoc.", Map.of("run()", "Runs from javadoc."));

        Map<TypeName, TypeEntry> result = resolve(resolver(sources, javadoc, group), group);

        TypeEntry foo = result.get(TypeName.of("p.Foo"));
        assertThat(foo.type().docSource()).isEqualTo(DocSource.SOURCES);
        assertThat(foo.type().entryPath()).isEqualTo("p/Foo.java");
        assertThat(foo.type().firstSentence()).isEqualTo("Foo from source.");
        assertThat(foo.type().supertypes()).containsExactly(exactBase);
        assertThat(foo.type().declarationRange()).isPresent();
        assertThat(signatures(foo)).containsExactly("void run()", "Foo()");
        assertThat(foo.memberDescriptors().getFirst().firstSentence()).isEqualTo("Runs.");
        assertThat(foo.doc().description()).isEqualTo("Foo from source.");
        assertThat(foo.memberDocs()).extracting(RenderedDoc::description).containsExactly("Runs.", "");
        TypeEntry inner = result.get(TypeName.of("p.Foo$Inner"));
        assertThat(inner.type().docSource()).isEqualTo(DocSource.SOURCES);
        assertThat(inner.doc().firstSentence()).isEqualTo("Nested from source.");
        assertThat(inner.type().firstSentence()).isEqualTo("Nested from source.");
        assertThat(signatures(inner)).containsExactly("Inner()");
    }

    @Test
    void typeMissingFromParsedFileFallsThroughOnItsOwn() {
        List<ScannedClass> group = List.of(Scanned.type("p.Foo", "run"), Scanned.type("p.Foo$Generated", "make"),
                Scanned.type("p.Foo$Other"));
        MapSourceArchive sources = new MapSourceArchive().with("p/Foo.java", FOO_JAVA);
        MapJavadocArchive javadoc = new MapJavadocArchive()
                .withTypePage("p/Foo.Generated.html", "Generated thing.", Map.of("make()", "Makes it."));

        Map<TypeName, TypeEntry> result = resolve(resolver(sources, javadoc, group), group);

        assertThat(result.get(TypeName.of("p.Foo")).type().docSource()).isEqualTo(DocSource.SOURCES);
        TypeEntry generated = result.get(TypeName.of("p.Foo$Generated"));
        assertThat(generated.type().docSource()).isEqualTo(DocSource.JAVADOC);
        assertThat(generated.type().firstSentence()).isEqualTo("Generated thing.");
        assertThat(generated.memberDescriptors().getFirst().firstSentence()).isEqualTo("Makes it.");
        assertThat(result.get(TypeName.of("p.Foo$Other")).type().docSource()).isEqualTo(DocSource.DECOMPILED);
    }

    @Test
    void javadocPageWhenNoSourceFile() {
        List<ScannedClass> group = List.of(Scanned.type("p.Foo", "run", "stop"), Scanned.type("p.Foo$Inner"));
        MapJavadocArchive javadoc = new MapJavadocArchive()
                .withTypePage("p/Foo.html", "Foo from javadoc. More.", Map.of("run()", "Runs from javadoc."))
                .withTypePage("p/Foo.Inner.html", "Inner page.", Map.of());

        Map<TypeName, TypeEntry> result = resolve(resolver(new MapSourceArchive(), javadoc, group), group);

        TypeEntry foo = result.get(TypeName.of("p.Foo"));
        assertThat(foo.type().docSource()).isEqualTo(DocSource.JAVADOC);
        assertThat(foo.type().entryPath()).isEqualTo("p/Foo.html");
        assertThat(foo.type().firstSentence()).isEqualTo("Foo from javadoc.");
        assertThat(foo.memberDescriptors()).extracting(MemberDescriptor::firstSentence)
                .containsExactly("Runs from javadoc.", "");
        assertThat(foo.doc().description()).isEqualTo("Foo from javadoc. More.");
        assertThat(foo.memberDocs().getFirst().description()).isEqualTo("Runs from javadoc.");
        assertThat(foo.memberDocs().get(1)).isEqualTo(RenderedDoc.missing());
        TypeEntry inner = result.get(TypeName.of("p.Foo$Inner"));
        assertThat(inner.type().docSource()).isEqualTo(DocSource.JAVADOC);
        assertThat(inner.type().entryPath()).isEqualTo("p/Foo.Inner.html");
    }

    @Test
    void decompiledWhenNeitherSourceNorPage() {
        List<ScannedClass> group = List.of(Scanned.type("p.Foo", "run"));

        TypeEntry foo = resolve(resolver(new MapSourceArchive(), new MapJavadocArchive(), group), group)
                .get(TypeName.of("p.Foo"));

        assertThat(foo.type().docSource()).isEqualTo(DocSource.DECOMPILED);
        assertThat(foo.type().entryPath()).isEqualTo("p/Foo.class");
        assertThat(foo.type().firstSentence()).isEmpty();
        assertThat(signatures(foo)).containsExactly("void run()");
        assertThat(foo.doc()).isEqualTo(RenderedDoc.missing());
        assertThat(foo.memberDocs()).containsOnly(RenderedDoc.missing());
    }

    @Test
    void packageDocPrefersANonEmptyPackageInfoOverThePackagePage() {
        List<ScannedClass> group = List.of(Scanned.type("p.Foo"));
        MapSourceArchive sources = new MapSourceArchive()
                .with("p/package-info.java", "/** Package p from source. */ package p;")
                .with("q/package-info.java", "package q;");
        MapJavadocArchive javadoc = new MapJavadocArchive()
                .withPackagePage("p/package-summary.html", "Package p from javadoc.")
                .withPackagePage("q/package-summary.html", "Package q from javadoc.");
        TypeDocResolver resolver = resolver(sources, javadoc, group);

        assertThat(resolver.packageDoc("p")).map(RenderedDoc::description).contains("Package p from source.");
        assertThat(resolver.packageDoc("q")).map(RenderedDoc::description).contains("Package q from javadoc.");
        assertThat(resolver.packageDoc("r")).isEmpty();
        assertThat(resolver(null, null, group).packageDoc("p")).isEmpty();
    }

    @Test
    void withoutAnyDocJarEverythingIsDecompiled() {
        List<ScannedClass> group = List.of(Scanned.type("p.Foo", "run"), Scanned.type("p.Foo$Inner"));

        Map<TypeName, TypeEntry> result = resolve(resolver(null, null, group), group);

        assertThat(result.values()).extracting(e -> e.type().docSource()).containsOnly(DocSource.DECOMPILED);
    }

    @Test
    void kotlinSourceDoesNotCount() {
        List<ScannedClass> group = List.of(Scanned.type("p.Util", "help"));
        MapSourceArchive sources = new MapSourceArchive().with("p/Util.kt", "package p\nobject Util { fun help() {} }");

        TypeEntry withoutPage = resolve(resolver(sources, new MapJavadocArchive(), group), group)
                .get(TypeName.of("p.Util"));
        TypeEntry withPage = resolve(resolver(sources,
                new MapJavadocArchive().withTypePage("p/Util.html", "Utility.", Map.of()), group), group)
                .get(TypeName.of("p.Util"));

        assertThat(withoutPage.type().docSource()).isEqualTo(DocSource.DECOMPILED);
        assertThat(withPage.type().docSource()).isEqualTo(DocSource.JAVADOC);
    }

    @Test
    void sourceWinsOverJavadocEvenWithoutComment() {
        List<ScannedClass> group = List.of(Scanned.type("p.Bare", "run"));
        MapSourceArchive sources = new MapSourceArchive().with("p/Bare.java",
                "package p; public class Bare { public void run() {} }");
        MapJavadocArchive javadoc = new MapJavadocArchive().withTypePage("p/Bare.html", "Documented.", Map.of());

        TypeEntry bare = resolve(resolver(sources, javadoc, group), group).get(TypeName.of("p.Bare"));

        assertThat(bare.type().docSource()).isEqualTo(DocSource.SOURCES);
        assertThat(bare.type().firstSentence()).isEmpty();
    }

    @Test
    void sourceTypesKeepPackagePrivateTypesButOnlyApiMembers() {
        List<ScannedClass> group = List.of(Scanned.type("p.Hidden"));
        MapSourceArchive sources = new MapSourceArchive().with("p/Hidden.java", """
                package p;
                class Hidden {
                    public int size() { return 0; }
                    protected void grow() {}
                    void internal() {}
                }
                """);

        TypeEntry hidden = resolve(resolver(sources, null, group), group).get(TypeName.of("p.Hidden"));

        assertThat(hidden.type().visibility().isApi()).isFalse();
        assertThat(signatures(hidden)).containsExactly("int size()", "protected void grow()");
    }
}
