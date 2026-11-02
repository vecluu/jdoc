package io.github.vecluu.jdoc.javadoc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vecluu.jdoc.bytecode.ScannedClass;
import io.github.vecluu.jdoc.bytecode.ZipBinaryJarScanner;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.RenderedDoc;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.source.JavaSource;
import io.github.vecluu.jdoc.source.JavaSourceParser;
import io.github.vecluu.jdoc.source.ParsedMember;
import io.github.vecluu.jdoc.source.ParsedType;
import io.github.vecluu.jdoc.testsupport.BuiltLibrary;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reads the sample library's real JDK 25 javadoc jar together with its binary jar. */
class JavadocJarIT {

    @TempDir
    static Path tmp;

    private static JavadocJar javadocJar;
    private static JavadocJarReader reader;
    private static Map<TypeName, ScannedClass> classes;

    @BeforeAll
    static void build() {
        BuiltLibrary library = SampleLibrary.builder().parameters(true).withSources(false).build().writeTo(tmp);
        javadocJar = JavadocJar.open(library.javadocJar().orElseThrow());
        reader = new JavadocJarReader(javadocJar, new JavadocPageParser());
        Map<TypeName, ScannedClass> result = new LinkedHashMap<>();
        new ZipBinaryJarScanner().scan(library.binaryJar(), c -> result.put(c.type().name(), c));
        classes = result;
    }

    @AfterAll
    static void close() {
        javadocJar.close();
    }

    private static JavadocTypePage page(TypeName type) {
        return reader.typePage(type).orElseThrow();
    }

    private static RenderedDoc memberDoc(TypeName type, String signature) {
        MemberDescriptor member = classes.get(type).members().stream()
                .filter(m -> m.signature().equals(signature)).findFirst().orElseThrow();
        return page(type).memberDoc(member).orElseThrow();
    }

    @Test
    void everyApiTypeExceptNonJavadocOnesHasAPage() {
        for (TypeName type : SampleLibrary.API_TYPES) {
            assertThat(reader.hasTypePage(type)).as("page of %s", type).isTrue();
        }
        assertThat(reader.hasTypePage(SampleLibrary.SHAPE_CACHE)).isFalse();
    }

    @Test
    void typeDocs() {
        RenderedDoc shape = page(SampleLibrary.SHAPE).typeDoc();
        assertThat(shape.firstSentence()).isEqualTo("A two-dimensional shape with an area.");
        assertThat(shape.description()).isEqualTo("""
                A two-dimensional shape with an area.

                Implementations must be immutable. Use Shapes#largest(Shape...) to find the largest shape, e.g. \
                when sorting a drawing.""");
        assertThat(shape.blockTags()).containsExactly("@since 1.0");

        RenderedDoc circle = page(SampleLibrary.CIRCLE).typeDoc();
        assertThat(circle.description()).isEqualTo("""
                A circle defined by its radius.

                Circle c = new Circle(2.0);
                double a = c.area();""");
        assertThat(circle.blockTags()).containsExactly("@see Square", "@since 1.0");

        assertThat(page(SampleLibrary.OLD_SHAPES).typeDoc().blockTags())
                .containsExactly("@deprecated use Shapes instead");
        assertThat(page(SampleLibrary.POINT).typeDoc().blockTags())
                .containsExactly("@param x the x coordinate", "@param y the y coordinate", "@since 1.0");
        assertThat(page(SampleLibrary.SQUARE).typeDoc()).isEqualTo(RenderedDoc.missing());
    }

    @Test
    void nestedTypePages() {
        assertThat(page(SampleLibrary.SHAPES_REGISTRY_ENTRY).typeDoc().blockTags())
                .containsExactly("@param name the registered name", "@param shape the shape");
        assertThat(page(SampleLibrary.SQUARE_CORNER).typeDoc().firstSentence())
                .isEqualTo("A corner of this square (an inner, non-static class).");
        assertThat(memberDoc(SampleLibrary.SHAPES_REGISTRY,
                "Shapes.Registry.Entry register(String name, Shape shape)").blockTags())
                .containsExactly("@param name the name", "@param shape the shape", "@return the new entry");
        assertThat(memberDoc(SampleLibrary.SHAPES_VISITOR, "R visitCircle(Circle circle)").firstSentence())
                .isEqualTo("Visits a circle.");
    }

    @Test
    void overloadsResolveToTheirOwnSections() {
        assertThat(memberDoc(SampleLibrary.SHAPES, "static <T extends Comparable<? super T>> T max(T a, T b)")
                .blockTags()).containsExactly("@param <T> the value type", "@param a the first value",
                "@param b the second value", "@return the larger value, `a` if both are equal");
        assertThat(memberDoc(SampleLibrary.SHAPES, "static int max(int a, int b)").firstSentence())
                .isEqualTo("Returns the larger of two ints.");
        assertThat(memberDoc(SampleLibrary.SHAPES, "static double max(double... values)").blockTags())
                .contains("@throws IllegalArgumentException if no values are given");
        assertThat(memberDoc(SampleLibrary.BOX, "Box<T> put(T item)").firstSentence()).isEqualTo("Adds a shape.");
        assertThat(memberDoc(SampleLibrary.BOX, "Box<T> put(T item, int times)").firstSentence())
                .isEqualTo("Adds a shape several times.");
        assertThat(memberDoc(SampleLibrary.CIRCLE, "Circle(double radius, Color color)").blockTags())
                .contains("@throws IllegalArgumentException if `radius` is negative");
    }

    @Test
    void memberDocsOfAllKinds() {
        assertThat(memberDoc(SampleLibrary.COLOR, "BLUE").description()).isEqualTo("Pure blue, a cool color.");
        assertThat(memberDoc(SampleLibrary.POINT, "static final Point ORIGIN").description())
                .isEqualTo("The origin, `(0, 0)`.");
        assertThat(memberDoc(SampleLibrary.SHAPE, "String DEFAULT_UNIT").blockTags()).isEmpty();
        assertThat(memberDoc(SampleLibrary.EXPERIMENTAL, "String value()").blockTags())
                .containsExactly("@return the reason");
        assertThat(memberDoc(SampleLibrary.CIRCLE, "double diameter()").blockTags()).containsExactly(
                "@return the diameter", "@deprecated use #radius() times two; will be removed in 2.0");
        assertThat(memberDoc(SampleLibrary.OLD_SHAPES, "static double sum(Shape a, Shape b)").blockTags())
                .doesNotContain("@deprecated");
        assertThat(memberDoc(SampleLibrary.SQUARE, "double area()").description())
                .as("javadoc copies docs of undocumented overrides").isEqualTo("Returns the area of this shape.");
    }

    @Test
    void packageDoc() {
        RenderedDoc doc = reader.packageDoc(SampleLibrary.PACKAGE).orElseThrow();

        assertThat(doc.firstSentence()).isEqualTo("Sample shapes library used by jdoc tests.");
        assertThat(doc.blockTags()).containsExactly("@since 1.0");
    }

    @Test
    void docsMatchSourceRenderingForExplicitComments() {
        Map<TypeName, ParsedType> sourceTypes = new HashMap<>();
        try (JavaSourceParser parser = new JavaSourceParser()) {
            SampleLibrary.sources().forEach((path, text) -> parser.parse(new JavaSource(path, text,
                    DocSource.SOURCES)).types().forEach(type -> sourceTypes.put(type.name(), type)));
        }
        int compared = 0;
        for (ScannedClass binary : classes.values()) {
            if (!reader.hasTypePage(binary.type().name())) {
                continue;
            }
            JavadocTypePage page = page(binary.type().name());
            ParsedType source = sourceTypes.get(binary.type().name());
            assertThat(page.typeDoc()).as("type doc of %s", binary.type().name()).isEqualTo(source.doc());
            compared++;
            for (MemberDescriptor member : binary.members()) {
                List<ParsedMember> candidates = source.members().stream()
                        .filter(m -> m.descriptor().erasedKey().equals(member.erasedKey())).toList();
                if (candidates.isEmpty() || candidates.getFirst().implicit()
                        || candidates.getFirst().doc().inheritsDoc()) {
                    continue;
                }
                assertThat(page.memberDoc(member)).as("doc of %s", member.reference())
                        .contains(candidates.getFirst().doc());
                compared++;
            }
        }
        assertThat(compared).isGreaterThan(50);
    }
}
