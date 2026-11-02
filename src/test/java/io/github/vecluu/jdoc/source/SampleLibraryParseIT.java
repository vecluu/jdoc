package io.github.vecluu.jdoc.source;

import static io.github.vecluu.jdoc.source.ParsedSources.descriptor;
import static io.github.vecluu.jdoc.source.ParsedSources.member;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import io.github.vecluu.jdoc.testsupport.BuiltLibrary;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/** Parses every {@code .java} entry of the real sample-library sources jar. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SampleLibraryParseIT {

    private final JavaSourceParser parser = new JavaSourceParser();
    private final Map<String, String> texts = new LinkedHashMap<>();
    private final Map<TypeName, ParsedType> types = new LinkedHashMap<>();
    private ParsedSourceFile packageInfo;

    @BeforeAll
    void parseSourcesJar(@TempDir Path tmp) throws IOException {
        BuiltLibrary library = SampleLibrary.complete().writeTo(tmp);
        List<TypeName> known = new ArrayList<>();
        try (ZipFile zip = new ZipFile(library.sourcesJar().orElseThrow().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".java")) {
                    texts.put(entry.getName(),
                            new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
                    if (!entry.getName().endsWith("-info.java")) {
                        known.add(TypeName.fromDisplayName(
                                entry.getName().replace(".java", "").replace('/', '.'), 0));
                    }
                }
            }
        }
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            ParsedSourceFile file = parser.parse(new JavaSource(entry.getKey(), entry.getValue(), DocSource.SOURCES),
                    KnownTypes.of(known));
            assertThat(file.problems()).as(entry.getKey()).isEmpty();
            if (entry.getKey().endsWith("package-info.java")) {
                packageInfo = file;
            }
            file.types().forEach(type -> types.put(type.name(), type));
        }
    }

    @AfterAll
    void close() {
        parser.close();
    }

    private ParsedType type(TypeName name) {
        assertThat(types).containsKey(name);
        return types.get(name);
    }

    @Test
    void everyApiTypeIsFound() {
        List<TypeName> api = types.values().stream()
                .map(ParsedType::descriptor)
                .filter(d -> d.visibility().isApi())
                .map(TypeDescriptor::name)
                .sorted()
                .toList();
        assertThat(api).containsExactlyInAnyOrderElementsOf(SampleLibrary.API_TYPES);
        assertThat(type(SampleLibrary.SHAPE_CACHE).descriptor().visibility()).isEqualTo(Visibility.PACKAGE);
    }

    @Test
    void typeDescriptors() {
        TypeDescriptor shape = type(SampleLibrary.SHAPE).descriptor();
        assertThat(shape.kind()).isEqualTo(TypeKind.INTERFACE);
        assertThat(shape.supertypes()).containsExactly(TypeName.of("java.lang.Comparable"));
        assertThat(shape.firstSentence()).isEqualTo("A two-dimensional shape with an area.");
        assertThat(shape.entryPath()).isEqualTo("com/example/sample/Shape.java");
        assertThat(shape.docSource()).isEqualTo(DocSource.SOURCES);
        TypeDescriptor circle = type(SampleLibrary.CIRCLE).descriptor();
        assertThat(circle.modifiers()).isEqualTo("public final");
        assertThat(circle.supertypes()).containsExactly(SampleLibrary.ABSTRACT_SHAPE);
        assertThat(type(SampleLibrary.ABSTRACT_SHAPE).descriptor().supertypes())
                .containsExactly(TypeName.of("java.lang.Object"), SampleLibrary.SHAPE);
        TypeDescriptor box = type(SampleLibrary.BOX).descriptor();
        assertThat(box.typeParameters()).isEqualTo("<T extends Shape>");
        assertThat(type(SampleLibrary.SHAPES_VISITOR).descriptor().typeParameters()).isEqualTo("<R>");
        assertThat(type(SampleLibrary.SHAPES_REGISTRY_ENTRY).descriptor().kind()).isEqualTo(TypeKind.RECORD);
        assertThat(type(SampleLibrary.SHAPES_REGISTRY_ENTRY).descriptor().modifiers()).isEqualTo("public static");
        assertThat(type(SampleLibrary.SQUARE_CORNER).descriptor().modifiers()).isEqualTo("public");
        assertThat(type(SampleLibrary.OLD_SHAPES).descriptor().deprecated()).isTrue();
        assertThat(type(SampleLibrary.SQUARE).descriptor().firstSentence()).isEmpty();
        assertThat(type(SampleLibrary.SQUARE).doc().inheritsDoc()).isTrue();
        assertThat(type(SampleLibrary.EXPERIMENTAL).descriptor().kind()).isEqualTo(TypeKind.ANNOTATION);
        assertThat(type(SampleLibrary.COLOR).descriptor().kind()).isEqualTo(TypeKind.ENUM);
    }

    @Test
    void shapeMembers() {
        ParsedType shape = type(SampleLibrary.SHAPE);
        assertThat(apiSignatures(shape)).containsExactly(
                "String DEFAULT_UNIT",
                "double area()",
                "String name()",
                "Shape scale(double factor)",
                "default String describe()",
                "default int compareTo(Shape other)",
                "static Shape unit()");
        assertThat(member(shape, "scale(double)").doc()).isEqualTo(new RenderedDoc(
                "Scales this shape by a factor.\n\nThe result is a new shape; this shape is unchanged.",
                "Scales this shape by a factor.",
                List.of("@param factor the scale factor, must be positive", "@return the scaled shape",
                        "@throws IllegalArgumentException if `factor` is not positive"),
                false));
        assertThat(shape.doc().description()).contains("Use Shapes#largest(Shape...) to find the largest shape");
        assertThat(shape.doc().blockTags()).containsExactly("@since 1.0");
        assertThat(descriptor(shape, "compareTo(Shape)").firstSentence()).isEqualTo("Compares shapes by area.");
    }

    @Test
    void abstractShapeMembers() {
        ParsedType abstractShape = type(SampleLibrary.ABSTRACT_SHAPE);
        assertThat(apiSignatures(abstractShape)).containsExactly(
                "protected final Color color",
                "protected AbstractShape(Color color)",
                "Color color()",
                "String name()",
                "protected static double checkFactor(double factor)");
        ParsedMember name = member(abstractShape, "name()");
        assertThat(name.doc().inheritsDoc()).isTrue();
        assertThat(name.doc().description()).isEqualTo("This implementation returns the simple class name.");
        assertThat(descriptor(abstractShape, "secret()").visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(descriptor(abstractShape, "packageHelper()").visibility()).isEqualTo(Visibility.PACKAGE);
    }

    @Test
    void circleMembers() {
        ParsedType circle = type(SampleLibrary.CIRCLE);
        assertThat(apiSignatures(circle)).containsExactly(
                "Circle(double radius)",
                "Circle(double radius, Color color)",
                "double radius()",
                "double area()",
                "Circle scale(double factor)",
                "double diameter()");
        assertThat(circle.doc().description()).isEqualTo("""
                A circle defined by its radius.

                Circle c = new Circle(2.0);
                double a = c.area();""");
        assertThat(circle.doc().blockTags()).containsExactly("@see Square", "@since 1.0");
        assertThat(member(circle, "area()").doc().inheritsDoc()).isTrue();
        assertThat(member(circle, "scale(double)").doc()).isEqualTo(RenderedDoc.missing());
        MemberDescriptor diameter = descriptor(circle, "diameter()");
        assertThat(diameter.deprecated()).isTrue();
        assertThat(member(circle, "diameter()").doc().blockTags())
                .contains("@deprecated use #radius() times two; will be removed in 2.0");
    }

    @Test
    void squareAndInnerClass() {
        assertThat(apiSignatures(type(SampleLibrary.SQUARE))).containsExactly(
                "Square(double side)", "double side()", "double area()", "Square scale(double factor)");
        assertThat(apiSignatures(type(SampleLibrary.SQUARE_CORNER))).containsExactly("Square owner()", "Corner()");
    }

    @Test
    void colorEnum() {
        ParsedType color = type(SampleLibrary.COLOR);
        assertThat(apiSignatures(color)).containsExactly(
                "RED", "GREEN", "BLUE", "String hex()", "boolean isCool()",
                "static Color[] values()", "static Color valueOf(String name)");
        assertThat(descriptor(color, "BLUE").firstSentence()).isEqualTo("Pure blue, a cool color.");
        assertThat(descriptor(color, "hex()").firstSentence()).isEqualTo("Returns the hex code, e.g. `#ff0000`.");
        assertThat(descriptor(color, "Color(String)").visibility()).isEqualTo(Visibility.PRIVATE);
    }

    @Test
    void pointRecord() {
        ParsedType point = type(SampleLibrary.POINT);
        assertThat(apiSignatures(point)).containsExactly(
                "static final Point ORIGIN", "Point(int x, int y)", "int distance(Point other)", "String toString()",
                "int hashCode()", "boolean equals(Object o)", "int x()", "int y()");
        assertThat(member(point, "equals(Object)").implicit()).isTrue();
        assertThat(member(point, "hashCode()").doc().firstSentence())
                .isEqualTo("Returns a hash code value for this object.");
        ParsedMember canonical = member(point, "Point(int,int)");
        assertThat(canonical.implicit()).isFalse();
        assertThat(canonical.doc().firstSentence()).isEqualTo("Creates a point.");
        assertThat(canonical.doc().blockTags())
                .containsExactly("@throws IllegalArgumentException if a coordinate is Integer#MIN_VALUE");
        assertThat(member(point, "x()").doc().blockTags()).containsExactly("@return the x coordinate");
        assertThat(descriptor(point, "ORIGIN").firstSentence()).isEqualTo("The origin, `(0, 0)`.");
    }

    @Test
    void shapesUtilityAndNestedTypes() {
        ParsedType shapes = type(SampleLibrary.SHAPES);
        assertThat(apiSignatures(shapes)).containsExactly(
                "static <T extends Comparable<? super T>> T max(T a, T b)",
                "static int max(int a, int b)",
                "static double max(double... values)",
                "static Optional<Shape> largest(Shape... shapes)",
                "static double totalArea(List<? extends Shape> shapes)",
                "static Map<String, List<Shape>> byName(Collection<? extends Shape> shapes)");
        assertThat(shapes.members()).extracting(m -> m.descriptor().erasedKey()).contains(
                "max(Comparable,Comparable)", "max(int,int)", "max(double[])", "largest(Shape[])");
        assertThat(shapes.doc().description()).isEqualTo("""
                Static helpers for shapes.

                All methods throw NullPointerException for `null` arguments. Highlights:

                - #largest(Shape...) finds the largest shape
                - #max(Comparable, Comparable) compares any values""");
        assertThat(apiSignatures(type(SampleLibrary.SHAPES_REGISTRY))).containsExactly(
                "Shapes.Registry.Entry register(String name, Shape shape)",
                "Optional<Shapes.Registry.Entry> find(String name)",
                "Registry()");
        assertThat(apiSignatures(type(SampleLibrary.SHAPES_REGISTRY_ENTRY))).containsExactly(
                "Entry(String name, Shape shape)", "String toString()", "int hashCode()", "boolean equals(Object o)",
                "String name()", "Shape shape()");
        assertThat(apiSignatures(type(SampleLibrary.SHAPES_VISITOR))).containsExactly(
                "R visitCircle(Circle circle)", "R visitOther(Shape shape)");
    }

    @Test
    void boxGenerics() {
        ParsedType box = type(SampleLibrary.BOX);
        assertThat(apiSignatures(box)).containsExactly(
                "Box()",
                "Box<T> put(T item)",
                "Box<T> put(T item, int times)",
                "List<T> items()",
                "<U extends Shape> Box<U> map(Function<? super T, ? extends U> mapper)",
                "protected void beforePut(T item)");
        assertThat(box.members()).extracting(m -> m.descriptor().erasedKey())
                .contains("put(Shape)", "put(Shape,int)", "map(Function)");
    }

    @Test
    void experimentalAnnotation() {
        assertThat(apiSignatures(type(SampleLibrary.EXPERIMENTAL)))
                .containsExactly("String value()", "String since()");
    }

    @Test
    void packageDocumentation() {
        assertThat(packageInfo.packageName()).isEqualTo(SampleLibrary.PACKAGE);
        RenderedDoc doc = packageInfo.packageDoc().orElseThrow();
        assertThat(doc.firstSentence()).isEqualTo("Sample shapes library used by jdoc tests.");
        assertThat(doc.blockTags()).containsExactly("@since 1.0");
    }

    @Test
    void rangesSliceTheirMembers() {
        MemberSourceSlicer slicer = new MemberSourceSlicer();
        String text = texts.get("com/example/sample/Circle.java");
        MemberDescriptor diameter = descriptor(type(SampleLibrary.CIRCLE), "diameter()");
        assertThat(slicer.member(text, diameter).orElseThrow())
                .startsWith("/**\n * Returns the diameter.")
                .contains("@Deprecated(since = \"1.1\")\npublic double diameter() {")
                .endsWith("    return radius * 2;\n}");
        assertThat(slicer.docComment(text, diameter).orElseThrow()).endsWith(" */").doesNotContain("public");
        for (ParsedType type : types.values()) {
            String source = texts.get(type.descriptor().entryPath());
            for (ParsedMember member : type.members()) {
                MemberDescriptor d = member.descriptor();
                assertThat(d.range().isPresent()).as(d.reference()).isEqualTo(!member.implicit());
                d.range().ifPresent(range -> assertThat(range.slice(source)).as(d.reference())
                        .contains(d.name()));
                d.docRange().ifPresent(doc -> {
                    assertThat(d.range().orElseThrow().contains(doc)).isTrue();
                    assertThat(doc.slice(source)).startsWith("/**").endsWith("*/");
                });
            }
        }
    }

    private static List<String> apiSignatures(ParsedType type) {
        return type.members().stream()
                .map(ParsedMember::descriptor)
                .filter(d -> d.visibility().isApi())
                .map(MemberDescriptor::signature)
                .toList();
    }
}
