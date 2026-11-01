package io.github.vecluu.jdoc.decompile;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.testsupport.BuiltLibrary;
import io.github.vecluu.jdoc.testsupport.SampleLibrary;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Decompiles the real sample library binary jar with Vineflower and checks the output parses as Java. */
class VineflowerDecompilerIT {

    @TempDir
    static Path tmp;

    private static BuiltLibrary library;

    private final VineflowerDecompiler decompiler = new VineflowerDecompiler();

    @BeforeAll
    static void buildLibrary() throws IOException {
        library = SampleLibrary.binaryOnly().writeTo(tmp.resolve("repo"));
    }

    @Test
    void genericClassWithOverloads() throws IOException {
        String text = decompiler.decompile(library.binaryJar(), SampleLibrary.BOX);

        Parsed parsed = parse(text, "Box");
        assertThat(parsed.classes()).contains("Box");
        assertThat(parsed.methods()).contains("put(T)", "put(T,int)", "map(Function<? super T, ? extends U>)",
                "items()", "beforePut(T)");
        assertThat(text).contains("class Box<T extends Shape>", "public <U extends Shape> Box<U> map(",
                "items.add(item);", "for (int i = 0; i < times; i++)");
    }

    @Test
    void utilityClassWithNestedTypesAndOverloads() throws IOException {
        String text = decompiler.decompile(library.binaryJar(), SampleLibrary.SHAPES_REGISTRY_ENTRY);

        Parsed parsed = parse(text, "Shapes");
        assertThat(parsed.classes()).contains("Shapes", "Registry", "Entry", "Visitor");
        assertThat(parsed.methods()).contains("max(T,T)", "max(int,int)", "max(double[])", "largest(Shape[])",
                "register(String,Shape)");
        assertThat(text).contains("throw new IllegalArgumentException(\"no values\");", "Math.max(a, b)", "double... values", "Shape... shapes",
                "record Entry(String name, Shape shape)");
    }

    @Test
    void enumWithConstantBody() throws IOException {
        String text = decompiler.decompile(library.binaryJar(), SampleLibrary.COLOR);

        Parsed parsed = parse(text, "Color");
        assertThat(parsed.classes()).contains("Color");
        assertThat(text).contains("enum Color", "RED", "BLUE");
        assertThat(text).doesNotContain("Color$1");
    }

    @Test
    void record() throws IOException {
        String text = decompiler.decompile(library.binaryJar(), SampleLibrary.POINT);

        Parsed parsed = parse(text, "Point");
        assertThat(parsed.classes()).contains("Point");
        assertThat(parsed.methods()).contains("distance(Point)");
        assertThat(text).contains("record Point(int x, int y)", "coordinate out of range",
                "Math.abs(this.x - other.x)");
    }

    @Test
    void classWithInnerClass() throws IOException {
        String text = decompiler.decompile(library.binaryJar(), SampleLibrary.SQUARE_CORNER);

        Parsed parsed = parse(text, "Square");
        assertThat(parsed.classes()).contains("Square", "Corner");
        assertThat(parsed.methods()).contains("area()", "scale(double)", "owner()");
        assertThat(text).contains("return Square.this;").doesNotContain("$VF");
    }

    @Test
    void outputIsDeterministicAndHasNoBanner() {
        String first = decompiler.decompile(library.binaryJar(), SampleLibrary.SHAPES);
        String second = decompiler.decompile(library.binaryJar(), SampleLibrary.SHAPES);

        assertThat(first).isEqualTo(second).startsWith("package com.example.sample;").doesNotContain("\r");
    }

    @Test
    void cacheDecompilesOnceAndKeepsFile() throws IOException, InterruptedException {
        DecompiledSourceCache cache = new DecompiledSourceCache(new CachePaths(tmp.resolve("cache")), decompiler);

        String first = cache.source(library.gav(), library.binaryJar(), SampleLibrary.CIRCLE);
        Path file = cache.cacheFile(library.gav(), SampleLibrary.CIRCLE);
        FileTime written = Files.getLastModifiedTime(file);
        Thread.sleep(20);
        String second = cache.source(library.gav(), library.binaryJar(), SampleLibrary.CIRCLE);

        assertThat(file).endsWith(Path.of("decompiled/com/example/sample-lib/1.0.0/com/example/sample/Circle.java"));
        assertThat(second).isEqualTo(first).contains("class Circle");
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(written);
    }

    private static Parsed parse(String text, String name) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///" + name + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null,
                List.of(file));
        List<String> classes = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        for (CompilationUnitTree unit : task.parse()) {
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitClass(ClassTree node, Void unused) {
                    classes.add(node.getSimpleName().toString());
                    return super.visitClass(node, unused);
                }

                @Override
                public Void visitMethod(MethodTree node, Void unused) {
                    List<String> params = node.getParameters().stream()
                            .map(p -> p.getType().toString())
                            .toList();
                    methods.add(node.getName() + "(" + String.join(",", params) + ")");
                    return super.visitMethod(node, unused);
                }
            }.scan(unit, null);
        }
        assertThat(diagnostics.getDiagnostics()).as("parse errors in:%n%s", text).isEmpty();
        return new Parsed(classes, methods);
    }

    private record Parsed(List<String> classes, List<String> methods) {
    }
}
