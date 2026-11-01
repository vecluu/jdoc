package io.github.vecluu.jdoc.testsupport;

import io.github.vecluu.jdoc.model.Gav;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.DocumentationTool;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * A test library defined by in-memory Java sources, compiled with the JDK compiler and packaged as a binary jar,
 * a sources jar and a javadoc jar (real JDK javadoc HTML from {@link ToolProvider#getSystemDocumentationTool()}).
 *
 * <pre>{@code
 * BuiltLibrary built = TestLibrary.builder(Gav.of("com.acme", "lib", "1.0"))
 *         .source("com/acme/Foo.java", "package com.acme; public class Foo {}")
 *         .omitFromSources("com/acme/Foo.java")        // simulate a generated class
 *         .extraSourceFile("com/acme/Foo.kt", "...")   // non-Java source in the sources jar
 *         .build()
 *         .writeTo(dir);
 * }</pre>
 *
 * <p>Source keys are paths relative to the source root ({@code a/b/Foo.java}). Compilation errors fail fast with
 * an {@link IllegalStateException} listing the diagnostics. Jars have sorted entries, directory entries and a fixed
 * entry timestamp, so their content is deterministic. Use {@link FakeMavenRepo} to lay them out as a repository.
 */
public final class TestLibrary {

    private final Gav gav;
    private final Map<String, String> javaSources;
    private final Map<String, String> extraSourceFiles;
    private final Set<String> omitFromSources;
    private final Set<String> omitFromJavadoc;
    private final boolean parameters;
    private final boolean debugInfo;
    private final boolean withSources;
    private final boolean withJavadoc;
    private final List<Path> classpath;

    private TestLibrary(Builder builder) {
        this.gav = builder.gav;
        this.javaSources = Map.copyOf(builder.javaSources);
        this.extraSourceFiles = Map.copyOf(builder.extraSourceFiles);
        this.omitFromSources = Set.copyOf(builder.omitFromSources);
        this.omitFromJavadoc = Set.copyOf(builder.omitFromJavadoc);
        this.parameters = builder.parameters;
        this.debugInfo = builder.debugInfo;
        this.withSources = builder.withSources;
        this.withJavadoc = builder.withJavadoc;
        this.classpath = List.copyOf(builder.classpath);
    }

    /** Starts a library with the given coordinates (used for jar file names). */
    public static Builder builder(Gav gav) {
        return new Builder(gav);
    }

    /** The library's coordinates. */
    public Gav gav() {
        return gav;
    }

    /** All Java sources, path to text (including those omitted from the sources jar). */
    public Map<String, String> javaSources() {
        return javaSources;
    }

    /**
     * Compiles the library and writes the requested jars, named after the {@link Gav}, into {@code directory}
     * (created if needed). A scratch directory {@code .work-<artifactId>} is used and deleted afterwards.
     */
    public BuiltLibrary writeTo(Path directory) {
        try {
            Files.createDirectories(directory);
            Path work = directory.resolve(".work-" + gav.artifactId());
            try {
                return build(directory, work);
            } finally {
                deleteRecursively(work);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BuiltLibrary build(Path directory, Path work) throws IOException {
        Path sourceRoot = work.resolve("src");
        Path classes = Files.createDirectories(work.resolve("classes"));
        List<Path> javaFiles = new ArrayList<>();
        for (Map.Entry<String, String> source : sorted(javaSources)) {
            javaFiles.add(write(sourceRoot, source.getKey(), source.getValue()));
        }
        compile(javaFiles, classes);

        Path binaryJar = directory.resolve(gav.fileName());
        JarWriter.writeDirectory(classes, binaryJar);

        Optional<Path> sourcesJar = Optional.empty();
        if (withSources) {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            javaSources.forEach((path, text) -> {
                if (!omitFromSources.contains(path)) {
                    entries.put(path, text.getBytes(StandardCharsets.UTF_8));
                }
            });
            extraSourceFiles.forEach((path, text) -> entries.put(path, text.getBytes(StandardCharsets.UTF_8)));
            Path jar = directory.resolve(gav.artifactId() + "-" + gav.version() + "-sources.jar");
            JarWriter.writeEntries(entries, jar);
            sourcesJar = Optional.of(jar);
        }

        Optional<Path> javadocJar = Optional.empty();
        if (withJavadoc) {
            Path apidocs = work.resolve("apidocs");
            List<Path> documented = javaFiles.stream()
                    .filter(file -> !omitFromJavadoc.contains(sourceRoot.relativize(file).toString()))
                    .toList();
            javadoc(documented, sourceRoot, apidocs);
            Path jar = directory.resolve(gav.artifactId() + "-" + gav.version() + "-javadoc.jar");
            JarWriter.writeDirectory(apidocs, jar);
            javadocJar = Optional.of(jar);
        }
        return new BuiltLibrary(gav, binaryJar, sourcesJar, javadocJar);
    }

    private void compile(List<Path> files, Path classes) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> options = new ArrayList<>(List.of(
                    "-d", classes.toString(), "-encoding", "UTF-8", "-proc:none", "-nowarn", "-Xlint:none"));
            options.add(debugInfo ? "-g" : "-g:none");
            options.addAll(classpathOptions());
            if (parameters) {
                options.add("-parameters");
            }
            StringWriter out = new StringWriter();
            boolean ok = compiler.getTask(out, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjectsFromPaths(files)).call();
            if (!ok) {
                throw new IllegalStateException("test library " + gav + " does not compile: "
                        + diagnostics.getDiagnostics() + out);
            }
        }
    }

    private void javadoc(List<Path> files, Path sourceRoot, Path apidocs) throws IOException {
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     tool.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> options = new ArrayList<>(classpathOptions());
            options.addAll(List.of(
                    "-d", apidocs.toString(),
                    "-sourcepath", sourceRoot.toString(),
                    "-encoding", "UTF-8", "-docencoding", "UTF-8", "-charset", "UTF-8",
                    "-notimestamp", "-quiet", "-Xdoclint:none", "-protected"));
            StringWriter out = new StringWriter();
            boolean ok = tool.getTask(out, fileManager, diagnostics, null, options,
                    fileManager.getJavaFileObjectsFromPaths(files)).call();
            if (!ok) {
                throw new IllegalStateException("javadoc failed for " + gav + ": "
                        + diagnostics.getDiagnostics() + out);
            }
        }
    }

    private List<String> classpathOptions() {
        if (classpath.isEmpty()) {
            return List.of();
        }
        return List.of("-classpath", classpath.stream().map(Path::toString)
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator)));
    }

    private static List<Map.Entry<String, String>> sorted(Map<String, String> map) {
        return map.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
    }

    private static Path write(Path root, String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Builder for {@link TestLibrary}. Defaults: no {@code -parameters}, debug info on, all three jars. */
    public static final class Builder {
        private final Gav gav;
        private final Map<String, String> javaSources = new LinkedHashMap<>();
        private final Map<String, String> extraSourceFiles = new LinkedHashMap<>();
        private final Set<String> omitFromSources = new LinkedHashSet<>();
        private final Set<String> omitFromJavadoc = new LinkedHashSet<>();
        private boolean parameters;
        private final List<Path> classpath = new ArrayList<>();
        private boolean debugInfo = true;
        private boolean withSources = true;
        private boolean withJavadoc = true;

        private Builder(Gav gav) {
            this.gav = gav;
        }

        /** Adds a Java source file, e.g. {@code source("a/b/Foo.java", "package a.b; ...")}. */
        public Builder source(String path, String text) {
            requireRelative(path, ".java");
            javaSources.put(path, text);
            return this;
        }

        /** Adds all Java source files of the map. */
        public Builder sources(Map<String, String> sources) {
            sources.forEach(this::source);
            return this;
        }

        /** Adds a non-Java file (e.g. {@code .kt}) that only goes into the sources jar. */
        public Builder extraSourceFile(String path, String text) {
            requireRelative(path, "");
            extraSourceFiles.put(path, text);
            return this;
        }

        /** Leaves a Java file out of the sources jar (it is still compiled and documented). */
        public Builder omitFromSources(String path) {
            omitFromSources.add(path);
            return this;
        }

        /** Leaves a Java file out of the javadoc run (no HTML page; still compiled and on the source path). */
        public Builder omitFromJavadoc(String path) {
            omitFromJavadoc.add(path);
            return this;
        }

        /** Compile with {@code -parameters} (MethodParameters attribute). */
        public Builder parameters(boolean value) {
            this.parameters = value;
            return this;
        }

        /** Compile with {@code -g} (default) or {@code -g:none}. */
        public Builder debugInfo(boolean value) {
            this.debugInfo = value;
            return this;
        }

        /** Whether to write a sources jar (default true). */
        public Builder withSources(boolean value) {
            this.withSources = value;
            return this;
        }

        /** Whether to write a javadoc jar (default true). */
        public Builder withJavadoc(boolean value) {
            this.withJavadoc = value;
            return this;
        }

        /** Compiles (and runs javadoc) against these jars, e.g. another test library's binary jar. */
        public Builder classpath(Path... jars) {
            classpath.addAll(List.of(jars));
            return this;
        }

        /** Creates the immutable library definition. */
        public TestLibrary build() {
            if (javaSources.isEmpty()) {
                throw new IllegalStateException("a test library needs at least one Java source");
            }
            for (String path : omitFromSources) {
                requireKnown(path);
            }
            for (String path : omitFromJavadoc) {
                requireKnown(path);
            }
            return new TestLibrary(this);
        }

        private void requireKnown(String path) {
            if (!javaSources.containsKey(path)) {
                throw new IllegalStateException("unknown source file: " + path);
            }
        }

        private static void requireRelative(String path, String suffix) {
            if (path.startsWith("/") || path.contains("..") || !path.endsWith(suffix) || path.isBlank()) {
                throw new IllegalArgumentException("not a relative " + suffix + " source path: " + path);
            }
        }
    }
}
