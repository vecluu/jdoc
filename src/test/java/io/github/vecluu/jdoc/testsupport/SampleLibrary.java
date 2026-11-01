package io.github.vecluu.jdoc.testsupport;

import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.TypeName;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shared sample library ({@code com.example:sample-lib:1.0.0}) that T04, T05, T06, T08, T09 and T10 test
 * against. Sources live in {@code src/test/resources/io/github/vecluu/jdoc/testsupport/sample-lib/}.
 *
 * <p>Contents of package {@code com.example.sample} (all documented unless noted):
 * <ul>
 *   <li>{@code Shape} — interface extending {@code Comparable<Shape>}: constant, abstract, default and static
 *       methods; {@code @param}/{@code @return}/{@code @throws}/{@code @since}, {@code {@link}},
 *       {@code {@linkplain}}, {@code <p>}, "e.g." in the first paragraph;</li>
 *   <li>{@code AbstractShape} — abstract class implementing Shape: protected field, protected constructor,
 *       protected static method, {@code {@inheritDoc}} with extra text, private and package-private members;</li>
 *   <li>{@code Circle} — final class extending AbstractShape: constructor overloads, {@code <pre>{@code ...}</pre>},
 *       {@code @see}, {@code {@inheritDoc}}-only method, undocumented covariant override {@code scale}
 *       (bridge method in bytecode), {@code @deprecated} + {@code @Deprecated(since)} method;</li>
 *   <li>{@code Square} — class without a class comment, undocumented overrides, inner (non-static) class
 *       {@code Square$Corner};</li>
 *   <li>{@code Color} — enum with documented constants, one constant with a body (anonymous {@code Color$1}),
 *       private constructor and field;</li>
 *   <li>{@code Point} — record with a documented compact constructor, a static constant and a method;</li>
 *   <li>{@code Shapes} — final utility class: generic method {@code <T extends Comparable<? super T>> T max(T, T)},
 *       overloads {@code max(int,int)} and {@code max(double...)}, varargs {@code largest(Shape...)}, wildcard
 *       parameters, a {@code <ul>} list; nested {@code Shapes$Registry}, deeper record
 *       {@code Shapes$Registry$Entry}, nested generic interface {@code Shapes$Visitor};</li>
 *   <li>{@code Box<T extends Shape>} — generic class with overloads {@code put(T)}/{@code put(T,int)}, generic
 *       method {@code map}, protected method, annotated with {@code @Experimental};</li>
 *   <li>{@code Experimental} — annotation with two elements;</li>
 *   <li>{@code OldShapes} — {@code @Deprecated} class;</li>
 *   <li>{@code ShapeCache} — package-private class (not API);</li>
 *   <li>{@code package-info.java} — package documentation.</li>
 * </ul>
 * Plus {@code com.example.sample.gen.GeneratedModel} and {@code com.example.sample.kt.KotlinUtils} (with a
 * {@code KotlinUtils.kt} stand-in), used by {@link #mixed()} to exercise the per-type doc-source fallback.
 */
public final class SampleLibrary {

    /** Coordinates of the sample library. */
    public static final Gav GAV = Gav.of("com.example", "sample-lib", "1.0.0");
    /** Main package. */
    public static final String PACKAGE = "com.example.sample";

    public static final TypeName SHAPE = type("Shape");
    public static final TypeName ABSTRACT_SHAPE = type("AbstractShape");
    public static final TypeName CIRCLE = type("Circle");
    public static final TypeName SQUARE = type("Square");
    public static final TypeName SQUARE_CORNER = type("Square$Corner");
    public static final TypeName COLOR = type("Color");
    public static final TypeName POINT = type("Point");
    public static final TypeName SHAPES = type("Shapes");
    public static final TypeName SHAPES_REGISTRY = type("Shapes$Registry");
    public static final TypeName SHAPES_REGISTRY_ENTRY = type("Shapes$Registry$Entry");
    public static final TypeName SHAPES_VISITOR = type("Shapes$Visitor");
    public static final TypeName BOX = type("Box");
    public static final TypeName EXPERIMENTAL = type("Experimental");
    public static final TypeName OLD_SHAPES = type("OldShapes");
    /** Package-private, not API. */
    public static final TypeName SHAPE_CACHE = type("ShapeCache");
    /** Only in binary and javadoc jars in {@link #mixed()}. */
    public static final TypeName GENERATED_MODEL = TypeName.of("com.example.sample.gen.GeneratedModel");
    /** Only {@code .kt} source and no javadoc page in {@link #mixed()}: falls back to decompiling. */
    public static final TypeName KOTLIN_UTILS = TypeName.of("com.example.sample.kt.KotlinUtils");

    /** All API (public/protected) types, top-level and nested, in binary-name order. */
    public static final List<TypeName> API_TYPES = List.of(
            ABSTRACT_SHAPE, BOX, CIRCLE, COLOR, EXPERIMENTAL, OLD_SHAPES, POINT, SHAPE, SHAPES,
            SHAPES_REGISTRY, SHAPES_REGISTRY_ENTRY, SHAPES_VISITOR, SQUARE, SQUARE_CORNER,
            GENERATED_MODEL, KOTLIN_UTILS);

    /** Source path of {@link #GENERATED_MODEL}. */
    public static final String GENERATED_MODEL_SOURCE = "com/example/sample/gen/GeneratedModel.java";
    /** Source path of {@link #KOTLIN_UTILS}. */
    public static final String KOTLIN_UTILS_SOURCE = "com/example/sample/kt/KotlinUtils.java";
    /** Path of the Kotlin stand-in file. */
    public static final String KOTLIN_UTILS_KT = "com/example/sample/kt/KotlinUtils.kt";

    /** Every Java source path of the library. */
    public static final List<String> JAVA_SOURCE_FILES = List.of(
            "com/example/sample/package-info.java",
            "com/example/sample/AbstractShape.java",
            "com/example/sample/Box.java",
            "com/example/sample/Circle.java",
            "com/example/sample/Color.java",
            "com/example/sample/Experimental.java",
            "com/example/sample/OldShapes.java",
            "com/example/sample/Point.java",
            "com/example/sample/Shape.java",
            "com/example/sample/ShapeCache.java",
            "com/example/sample/Shapes.java",
            "com/example/sample/Square.java",
            GENERATED_MODEL_SOURCE,
            KOTLIN_UTILS_SOURCE);

    private static final String RESOURCE_ROOT = "/io/github/vecluu/jdoc/testsupport/sample-lib/";

    private SampleLibrary() {
    }

    /** Text of one source file of the library (a path from {@link #JAVA_SOURCE_FILES} or {@link #KOTLIN_UTILS_KT}). */
    public static String source(String path) {
        try (InputStream in = SampleLibrary.class.getResourceAsStream(RESOURCE_ROOT + path)) {
            if (in == null) {
                throw new IllegalArgumentException("no sample source " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** All Java sources, path to text, in {@link #JAVA_SOURCE_FILES} order. */
    public static Map<String, String> sources() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String path : JAVA_SOURCE_FILES) {
            sources.put(path, source(path));
        }
        return sources;
    }

    /** A builder preloaded with every Java source; adjust and {@code build()}. */
    public static TestLibrary.Builder builder() {
        return TestLibrary.builder(GAV).sources(sources());
    }

    /** All three jars, complete sources and javadoc for every type. */
    public static TestLibrary complete() {
        return builder().build();
    }

    /**
     * Mixed doc sources: {@code GeneratedModel.java} missing from the sources jar (→ javadoc), {@code KotlinUtils}
     * present only as {@code .kt} and without a javadoc page (→ decompiled), everything else from sources.
     */
    public static TestLibrary mixed() {
        return builder()
                .omitFromSources(GENERATED_MODEL_SOURCE)
                .omitFromSources(KOTLIN_UTILS_SOURCE)
                .extraSourceFile(KOTLIN_UTILS_KT, source(KOTLIN_UTILS_KT))
                .omitFromJavadoc(KOTLIN_UTILS_SOURCE)
                .build();
    }

    /** Only the binary jar. */
    public static TestLibrary binaryOnly() {
        return builder().withSources(false).withJavadoc(false).build();
    }

    private static TypeName type(String classPart) {
        return TypeName.of(PACKAGE + "." + classPart);
    }
}
