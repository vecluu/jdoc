package io.github.vecluu.jdoc.lookup;

import static io.github.vecluu.jdoc.lookup.LookupIndex.method;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.decompile.DecompilationException;
import io.github.vecluu.jdoc.decompile.DecompiledSourceProvider;
import io.github.vecluu.jdoc.indexer.MemberEntry;
import io.github.vecluu.jdoc.indexer.TypeEntry;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.SourceRange;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceLoaderTest {

    private static final String DECOMPILED = """
            package d;

            public class Lib {
                public int twice(int value) {
                    return value * 2;
                }

                public static class Inner {
                    public String hello() {
                        return "hi";
                    }
                }
            }
            """;

    @TempDir
    Path tmp;

    private LookupIndex index;
    private LookupServices services;
    private final List<String> decompileCalls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        index = LookupIndex.open(tmp);
        SampleSources.addTo(index);
        index.add(LookupIndex.artifact(ArtifactKey.maven(Gav.parse("com.acme:dec:1.0"))).toBuilder()
                        .javadocJar(Optional.of(tmp.resolve("dec-javadoc.jar")))
                        .build(),
                List.of(new TypeEntry(LookupIndex.publicClass("d.Lib").toBuilder()
                                        .docSource(DocSource.JAVADOC).entryPath("d/Lib.html").build(),
                                RenderedDoc.missing(), List.of(
                                        new MemberEntry(method("d.Lib", "twice", "int value"), new RenderedDoc(
                                                "Doubles.", "Doubles.", List.of("@param value v"), false)),
                                        MemberEntry.undocumented(method("d.Lib", "missing")))),
                        TypeEntry.undocumented(LookupIndex.publicClass("d.Lib$Inner"),
                                List.of(method("d.Lib$Inner", "hello"))),
                        TypeEntry.undocumented(LookupIndex.publicClass("d.Broken"),
                                List.of(method("d.Broken", "boom")))),
                Map.of());
        index.add(LookupIndex.artifact(ArtifactKey.jdk(21)),
                List.of(LookupIndex.publicClass("java.util.NoSrc")), List.of(method("java.util.NoSrc", "x")));
        DecompiledSourceProvider decompiler = (gav, jar, type) -> {
            decompileCalls.add(gav + " " + type.binaryName());
            if (type.binaryName().equals("d.Broken")) {
                throw new DecompilationException("Cannot decompile d.Broken: corrupt class");
            }
            return DECOMPILED;
        };
        services = index.services(decompiler);
    }

    @AfterEach
    void tearDown() {
        services.close();
        index.close();
    }

    @Test
    void slicesMembersFromSourcesWithTheirJavadoc() {
        MemberSource.Available source = available("a.Impl#own");

        assertThat(source.code()).isEqualTo("""
                /** Does its own thing. */
                public void own() {
                    System.out.println("own");
                }""");
        assertThat(source.decompiled()).isFalse();
        assertThat(source.doc()).isEmpty();
    }

    @Test
    void slicesMembersFromJdkSourcesAndInheritedTargets() {
        assertThat(available("java.lang.Object#toString").code()).contains("public String toString() {")
                .startsWith("/**");
        MemberSource.Available inherited = available("a.Impl#name");
        assertThat(inherited.member().declaringType().name().simpleName()).isEqualTo("Base");
        assertThat(inherited.code()).contains("return \"base\";");
    }

    @Test
    void decompilesTypesWithoutSourcesAndAddsTheJavadocPageDoc() {
        MemberSource.Available source = available("d.Lib#twice");

        assertThat(source.code()).isEqualTo("""
                public int twice(int value) {
                    return value * 2;
                }""");
        assertThat(source.decompiled()).isTrue();
        assertThat(source.doc()).hasValueSatisfying(doc -> assertThat(doc.description()).isEqualTo("Doubles."));
        assertThat(decompileCalls).containsExactly("com.acme:dec:1.0 d.Lib");
    }

    @Test
    void decompiledNestedTypesAreFoundInTheTopLevelSource() {
        MemberSource.Available source = available("d.Lib.Inner#hello");

        assertThat(source.code()).contains("return \"hi\";");
        assertThat(source.doc()).isEmpty();
        assertThat(decompileCalls).containsExactly("com.acme:dec:1.0 d.Lib$Inner");
    }

    @Test
    void reportsMissingSourceAsAValue() {
        assertThat(unavailable("a.Point#x").reason()).contains("generated by the compiler");
        assertThat(unavailable("d.Lib#missing").reason()).contains("not found in decompiled source");
        assertThat(unavailable("d.Broken#boom").reason()).contains("Cannot decompile d.Broken");
        assertThat(unavailable("java.util.NoSrc#x").reason()).contains("src.zip");
    }

    @Test
    void sourceTextChangedSinceIndexingIsParsedAndTheMemberStillFound() {
        JarEntryReader real = new ZipJarEntryReader();
        JarEntryReader shifted = (jar, entry) -> real.readText(jar, entry)
                .map(text -> entry.endsWith("a/Impl.java") ? text.replace("package a;", "package a;\n\n// moved\n")
                        : text);
        try (LookupServices changed = index.services((gav, jar, type) -> {
            throw new AssertionError("unexpected decompile");
        }, shifted)) {
            MemberLookup.Found found = (MemberLookup.Found) changed.memberLookup().lookup("a.Impl#own", index.scope());
            MemberSource source = changed.sources().source(found.members().getFirst(), index.scope());

            assertThat(source).isInstanceOfSatisfying(MemberSource.Available.class, available ->
                    assertThat(available.code()).isEqualTo(available("a.Impl#own").code()));
        }
    }

    @Test
    void storedRangeMatchesOnlyWhileItStillPointsAtTheMember() {
        String text = "class A {\n    /** Doc. */\n    void own() {}\n    void owner() {}\n}\n";
        int start = text.indexOf("/**");
        int end = text.indexOf("{}") + 2;
        MemberDescriptor own = method("A", "own").toBuilder()
                .range(Optional.of(new SourceRange(start, end)))
                .docRange(Optional.of(new SourceRange(start, text.indexOf("*/") + 2)))
                .build();

        assertThat(SourceLoader.storedRangeMatches(text, own)).isTrue();
        assertThat(SourceLoader.storedRangeMatches("// shifted\n" + text, own)).isFalse();
        assertThat(SourceLoader.storedRangeMatches(text.replace("own()", "own2()"), own)).isFalse();
        assertThat(SourceLoader.storedRangeMatches(text.substring(0, end - 1), own)).isFalse();
        assertThat(SourceLoader.storedRangeMatches(text, own.toBuilder().docRange(Optional.empty()).build()))
                .isTrue();
        assertThat(SourceLoader.storedRangeMatches(text, own.toBuilder().range(Optional.empty()).build())).isFalse();
        int ownerStart = text.indexOf("void owner");
        MemberDescriptor wrongName = own.toBuilder().docRange(Optional.empty())
                .range(Optional.of(new SourceRange(ownerStart, text.indexOf("{}", ownerStart) + 2))).build();
        assertThat(SourceLoader.storedRangeMatches(text, wrongName)).isFalse();
    }

    private MemberSource source(String reference) {
        MemberLookup.Found found = (MemberLookup.Found) services.memberLookup().lookup(reference, index.scope());
        return services.sources().source(found.members().getFirst(), index.scope());
    }

    private MemberSource.Available available(String reference) {
        MemberSource source = source(reference);
        assertThat(source).as(reference).isInstanceOf(MemberSource.Available.class);
        return (MemberSource.Available) source;
    }

    private MemberSource.Unavailable unavailable(String reference) {
        MemberSource source = source(reference);
        assertThat(source).as(reference).isInstanceOf(MemberSource.Unavailable.class);
        return (MemberSource.Unavailable) source;
    }
}
