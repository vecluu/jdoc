package io.github.vecluu.jdoc.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSource;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.ParsedMember;
import io.github.vecluu.jdoc.source.ParsedType;
import io.github.vecluu.jdoc.testsupport.BuiltLibrary;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import io.github.vecluu.jdoc.testsupport.TestLibrary;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Scans the real sample library binary jar and compares the result with source parsing (T04). */
class SampleLibraryBytecodeIT {

    @TempDir
    static Path tmp;

    private static Map<TypeName, ScannedClass> scanned;
    private static JarScanSummary summary;

    @BeforeAll
    static void scan() {
        BuiltLibrary library = SampleLibrary.builder().parameters(true).withSources(false).withJavadoc(false)
                .build().writeTo(tmp.resolve("with-parameters"));
        Map<TypeName, ScannedClass> result = new LinkedHashMap<>();
        summary = new ZipBinaryJarScanner().scan(library.binaryJar(), c -> result.put(c.type().name(), c));
        scanned = result;
    }

    private static List<String> signatures(TypeName type) {
        return scanned.get(type).members().stream().map(MemberDescriptor::signature).toList();
    }

    @Test
    void listsEveryNamedType() {
        List<TypeName> expected = new ArrayList<>(SampleLibrary.API_TYPES);
        expected.add(SampleLibrary.SHAPE_CACHE);

        assertThat(scanned.keySet()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(summary.typeCount()).isEqualTo(expected.size());
        assertThat(summary.skippedEntries()).isZero();
        assertThat(scanned.values()).allSatisfy(c -> assertThat(c.type().docSource()).isEqualTo(DocSource.DECOMPILED));
    }

    @Test
    void fullMemberList() {
        assertThat(signatures(SampleLibrary.SHAPE)).containsExactly(
                "String DEFAULT_UNIT",
                "double area()",
                "String name()",
                "Shape scale(double factor)",
                "default String describe()",
                "default int compareTo(Shape other)",
                "static Shape unit()");
        assertThat(signatures(SampleLibrary.ABSTRACT_SHAPE)).containsExactly(
                "protected final Color color",
                "protected AbstractShape(Color color)",
                "Color color()",
                "String name()",
                "protected static double checkFactor(double factor)");
        assertThat(signatures(SampleLibrary.CIRCLE)).containsExactly(
                "Circle(double radius)",
                "Circle(double radius, Color color)",
                "double radius()",
                "double area()",
                "Circle scale(double factor)",
                "double diameter()");
        assertThat(signatures(SampleLibrary.SQUARE)).containsExactly(
                "Square(double side)", "double side()", "double area()", "Square scale(double factor)");
        assertThat(signatures(SampleLibrary.SQUARE_CORNER)).containsExactly("Corner()", "Square owner()");
        assertThat(signatures(SampleLibrary.COLOR)).containsExactly(
                "RED", "GREEN", "BLUE", "static Color[] values()", "static Color valueOf(String name)",
                "String hex()", "boolean isCool()");
        assertThat(signatures(SampleLibrary.POINT)).containsExactly(
                "static final Point ORIGIN",
                "Point(int x, int y)",
                "int distance(Point other)",
                "String toString()",
                "int hashCode()",
                "boolean equals(Object o)",
                "int x()",
                "int y()");
        assertThat(signatures(SampleLibrary.SHAPES)).containsExactly(
                "static <T extends Comparable<? super T>> T max(T a, T b)",
                "static int max(int a, int b)",
                "static double max(double... values)",
                "static Optional<Shape> largest(Shape... shapes)",
                "static double totalArea(List<? extends Shape> shapes)",
                "static Map<String, List<Shape>> byName(Collection<? extends Shape> shapes)");
        assertThat(signatures(SampleLibrary.SHAPES_REGISTRY)).containsExactly(
                "Registry()",
                "Shapes.Registry.Entry register(String name, Shape shape)",
                "Optional<Shapes.Registry.Entry> find(String name)");
        assertThat(signatures(SampleLibrary.SHAPES_REGISTRY_ENTRY)).containsExactly(
                "Entry(String name, Shape shape)", "String toString()", "int hashCode()", "boolean equals(Object o)",
                "String name()", "Shape shape()");
        assertThat(signatures(SampleLibrary.SHAPES_VISITOR)).containsExactly(
                "R visitCircle(Circle circle)", "R visitOther(Shape shape)");
        assertThat(signatures(SampleLibrary.BOX)).containsExactly(
                "Box()",
                "Box<T> put(T item)",
                "Box<T> put(T item, int times)",
                "List<T> items()",
                "<U extends Shape> Box<U> map(Function<? super T, ? extends U> mapper)",
                "protected void beforePut(T item)");
        assertThat(signatures(SampleLibrary.EXPERIMENTAL)).containsExactly(
                "String value()", "String since()");
        assertThat(signatures(SampleLibrary.OLD_SHAPES)).containsExactly("static double sum(Shape a, Shape b)");
        assertThat(signatures(SampleLibrary.SHAPE_CACHE)).containsExactly("int size()");
    }

    @Test
    void typeDescriptors() {
        TypeDescriptor box = scanned.get(SampleLibrary.BOX).type();
        assertThat(box.typeParameters()).isEqualTo("<T extends Shape>");
        assertThat(box.modifiers()).isEqualTo("public");
        assertThat(scanned.get(SampleLibrary.SHAPES_VISITOR).type().typeParameters()).isEqualTo("<R>");
        assertThat(scanned.get(SampleLibrary.SHAPES_REGISTRY).type().modifiers()).isEqualTo("public static final");
        assertThat(scanned.get(SampleLibrary.OLD_SHAPES).type().deprecated()).isTrue();
        assertThat(scanned.get(SampleLibrary.SHAPE).type().supertypes())
                .containsExactly(TypeName.of("java.lang.Comparable"));
        assertThat(scanned.get(SampleLibrary.CIRCLE).type().supertypes()).containsExactly(SampleLibrary.ABSTRACT_SHAPE);
        assertThat(scanned.get(SampleLibrary.SHAPE_CACHE).type().visibility().isApi()).isFalse();
        assertThat(scanned.get(SampleLibrary.CIRCLE).members().stream()
                .filter(MemberDescriptor::deprecated).map(MemberDescriptor::name)).containsExactly("diameter");
    }

    @Test
    void withoutParametersAttributeNamesAreArgN(@TempDir Path dir) {
        BuiltLibrary library = SampleLibrary.builder().withSources(false).withJavadoc(false).build().writeTo(dir);

        ScannedClass circle = new ZipBinaryJarScanner().readClass(library.binaryJar(), SampleLibrary.CIRCLE)
                .orElseThrow();
        assertThat(circle.members().stream().map(MemberDescriptor::signature))
                .contains("Circle(double arg0, Color arg1)", "Circle scale(double arg0)");
    }

    @Test
    void matchesSourceParsing() {
        assertThat(scanned).containsKeys(SampleLibrary.SHAPE, SampleLibrary.SHAPES_VISITOR, SampleLibrary.EXPERIMENTAL);
        assertMatchesSourceParsing(scanned, SampleLibrary.sources());
    }

    @Test
    void interfaceMembersMatchSourceParsing(@TempDir Path dir) {
        Map<String, String> sources = Map.of(
                "p/Api.java", """
                        package p;
                        /** Api. */
                        public interface Api<T> extends Comparable<Api<T>> {
                            /** Explicit modifiers. */
                            public static final int LIMIT = 3;
                            /** Implicit modifiers. */
                            String NAME = "n";
                            /** Explicit abstract. */
                            public abstract void run();
                            /** Implicit abstract. */
                            T get(int index, String... rest);
                            /** Default. */
                            default <U> U map(java.util.function.Function<? super T, U> f) { return null; }
                            /** Static. */
                            static <T> Api<T> empty() { return null; }
                            /** Nested. */
                            interface Inner { void accept(Object value); int SIZE = 1; }
                        }
                        """,
                "p/Marker.java", """
                        package p;
                        /** Marker. */
                        public @interface Marker { String value() default ""; Class<?>[] types(); int MAX = 9; }
                        """,
                "p/Holder.java", """
                        package p;
                        /** Holder. */
                        public abstract class Holder {
                            /** A class constant. */
                            public static final int MIN = 0;
                            /** Abstract method. */
                            protected abstract void run(int times);
                            /** Nested interface of a class. */
                            public interface Callback { abstract void done(boolean ok); }
                        }
                        """);
        BuiltLibrary library = TestLibrary.builder(Gav.of("t", "api", "1")).sources(sources).parameters(true)
                .withSources(false).withJavadoc(false).build().writeTo(dir);
        Map<TypeName, ScannedClass> binary = new LinkedHashMap<>();
        new ZipBinaryJarScanner().scan(library.binaryJar(), c -> binary.put(c.type().name(), c));

        assertMatchesSourceParsing(binary, sources);
        assertThat(binary.get(TypeName.of("p.Api")).members().stream().map(MemberDescriptor::signature))
                .containsExactly("int LIMIT", "String NAME", "void run()", "T get(int index, String... rest)",
                        "default <U> U map(Function<? super T, U> f)", "static <T> Api<T> empty()");
        assertThat(binary.get(TypeName.of("p.Holder")).members().stream().map(MemberDescriptor::signature))
                .contains("static final int MIN", "protected abstract void run(int times)");
        assertThat(binary.get(TypeName.of("p.Holder$Callback")).members().getFirst().signature())
                .isEqualTo("void done(boolean ok)");
    }

    private static void assertMatchesSourceParsing(Map<TypeName, ScannedClass> scanned, Map<String, String> sources) {
        Map<TypeName, ParsedType> sourceTypes = new HashMap<>();
        try (JavaSourceParser parser = new JavaSourceParser()) {
            sources.forEach((path, text) -> parser.parse(new JavaSource(path, text,
                    DocSource.SOURCES)).types().forEach(type -> sourceTypes.put(type.name(), type)));
        }

        for (ScannedClass binary : scanned.values()) {
            ParsedType source = sourceTypes.get(binary.type().name());
            assertThat(source).as("source type %s", binary.type().name()).isNotNull();
            assertThat(withoutDocFields(binary.type())).as("type %s", binary.type().name())
                    .isEqualTo(withoutDocFields(source.descriptor()));

            Map<String, MemberDescriptor> binaryMembers = new LinkedHashMap<>();
            binary.members().forEach(m -> binaryMembers.put(m.erasedKey(), m));
            for (ParsedMember member : source.members()) {
                MemberDescriptor expected = member.descriptor();
                if (!expected.visibility().isApi()) {
                    continue;
                }
                MemberDescriptor actual = binaryMembers.remove(expected.erasedKey());
                assertThat(actual).as("binary member for %s", expected.reference()).isNotNull();
                assertThat(withoutDocFields(actual)).as("member %s", expected.reference())
                        .isEqualTo(withoutDocFields(expected));
            }
            assertThat(binaryMembers.keySet()).as("binary-only members of %s", binary.type().name()).isEmpty();
        }
    }

    private static TypeDescriptor withoutDocFields(TypeDescriptor type) {
        return type.toBuilder().firstSentence("").docSource(DocSource.SOURCES).entryPath("x")
                .declarationRange(java.util.Optional.empty()).build();
    }

    private static MemberDescriptor withoutDocFields(MemberDescriptor member) {
        return member.toBuilder().firstSentence("").range(java.util.Optional.empty())
                .docRange(java.util.Optional.empty()).build();
    }
}
