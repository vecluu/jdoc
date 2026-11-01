package io.github.vecluu.jdoc.bytecode;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Test helper: compiles Java sources in memory and returns class files by internal name. */
final class InMemoryCompiler {

    private InMemoryCompiler() {
    }

    /** Compiles {@code path → source} with optional extra javac options; fails on compile errors. */
    static Map<String, byte[]> compile(Map<String, String> sources, String... options) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, ByteArrayOutputStream> outputs = new TreeMap<>();
        StandardJavaFileManager standard = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        JavaFileManager manager = new ForwardingJavaFileManager<>(standard) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind,
                                                       FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + ".class"),
                        kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        outputs.put(className.replace('.', '/'), out);
                        return out;
                    }
                };
            }
        };
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((path, text) -> units.add(new SimpleJavaFileObject(URI.create("mem:///" + path),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        }));
        List<String> allOptions = new ArrayList<>(List.of("-proc:none", "-Xlint:none", "-nowarn"));
        allOptions.addAll(List.of(options));
        boolean ok = compiler.getTask(null, manager, diagnostics, allOptions, null, units).call();
        if (!ok) {
            throw new IllegalStateException("compilation failed: " + diagnostics.getDiagnostics());
        }
        Map<String, byte[]> result = new TreeMap<>();
        outputs.forEach((name, out) -> result.put(name, out.toByteArray()));
        return result;
    }

    /** Compiles a single source file. */
    static Map<String, byte[]> compile(String path, String source, String... options) {
        return compile(Map.of(path, source), options);
    }
}
