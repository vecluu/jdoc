package io.github.vecluu.jdoc.cli;

import io.github.vecluu.jdoc.app.ExitCode;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.DocumentationTool;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Writes the throwaway library that {@code dist/install.sh} lets the AOT training run index, so the cache also holds
 * the code that indexes a new library (bytecode scanning, sources-jar parsing, javadoc HTML) and decompiles it. It is
 * built from {@link TrainingLibrarySources} with the JDK's own compiler and javadoc tool, in three
 * {@link Flavour flavours}, into a local Maven repository layout, together with the {@code mvn dependency:list}
 * output that names the three jars.
 *
 * <p>The installer runs it in its own JVM before the recording run ({@code java -cp jdoc.jar
 * io.github.vecluu.jdoc.cli.TrainingLibrary <repository> <dependency-list>}), so compiler and javadoc classes do
 * not end up in the cache. Not part of the command line.
 */
public final class TrainingLibrary {

    /** Group id of every flavour. */
    public static final String GROUP_ID = "jdoc.training";
    /** Version of every flavour. */
    public static final String VERSION = "1.0";

    private static final FileTime ENTRY_TIME = FileTime.fromMillis(0);

    /** How a flavour of the library ships, which decides where jdoc takes its docs from. */
    public enum Flavour {
        /** Binary jar plus sources jar: docs parsed from the sources. */
        SOURCES,
        /** Binary jar plus javadoc jar: docs from the javadoc HTML. */
        JAVADOC,
        /** Binary jar only: no docs, {@code source} decompiles. */
        BINARY;

        /** {@code training-sources}, {@code training-javadoc}, {@code training-binary}. */
        public String artifactId() {
            return "training-" + name().toLowerCase(Locale.ROOT);
        }

        /** The flavour's own package, {@code jdoc.training.sources} etc. */
        public String packageName() {
            return GROUP_ID + "." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The jars written for one flavour.
     *
     * @param flavour    the flavour
     * @param binaryJar  {@code <artifactId>-1.0.jar}
     * @param sourcesJar {@code <artifactId>-1.0-sources.jar}, for {@link Flavour#SOURCES}
     * @param javadocJar {@code <artifactId>-1.0-javadoc.jar}, for {@link Flavour#JAVADOC}
     */
    public record Artifact(Flavour flavour, Path binaryJar, Optional<Path> sourcesJar, Optional<Path> javadocJar) {
    }

    private final JavaCompiler compiler;
    private final DocumentationTool documentation;

    /**
     * @param compiler      compiles the sources
     * @param documentation writes the javadoc HTML
     */
    public TrainingLibrary(JavaCompiler compiler, DocumentationTool documentation) {
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        this.documentation = Objects.requireNonNull(documentation, "documentation tool must not be null");
    }

    /** The library built with the running JDK's tools; empty on a runtime without the compiler or javadoc. */
    public static Optional<TrainingLibrary> withSystemTools() {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DocumentationTool documentation = ToolProvider.getSystemDocumentationTool();
        if (compiler == null || documentation == null) {
            return Optional.empty();
        }
        return Optional.of(new TrainingLibrary(compiler, documentation));
    }

    /**
     * {@code <repository> <dependency-list>}: writes every flavour into the repository and the dependency list to
     * the second path. Exits 0, or 3 with one line on stderr.
     */
    public static void main(String[] args) {
        System.exit(run(args, System.err));
    }

    static int run(String[] args, PrintStream err) {
        if (args.length != 2) {
            err.println("usage: TrainingLibrary <repository> <dependency-list>");
            return ExitCode.ERROR.code();
        }
        Optional<TrainingLibrary> library = withSystemTools();
        if (library.isEmpty()) {
            err.println("error: this Java runtime has no compiler or javadoc tool");
            return ExitCode.ERROR.code();
        }
        try {
            List<Artifact> artifacts = library.get().writeTo(Path.of(args[0]));
            Path list = Path.of(args[1]);
            if (list.toAbsolutePath().getParent() != null) {
                Files.createDirectories(list.toAbsolutePath().getParent());
            }
            Files.writeString(list, dependencyList(artifacts), StandardCharsets.UTF_8);
            return ExitCode.FOUND.code();
        } catch (IOException | IllegalStateException e) {
            err.println("error: cannot write the training library: " + e.getMessage());
            return ExitCode.ERROR.code();
        }
    }

    /**
     * Compiles every flavour and writes its jars into {@code <repository>/jdoc/training/<artifactId>/1.0/}.
     *
     * @return the jars, in {@link Flavour} order
     * @throws IOException           if a file cannot be written
     * @throws IllegalStateException if the sources do not compile or javadoc fails (a bug)
     */
    public List<Artifact> writeTo(Path repository) throws IOException {
        Path root = Files.createDirectories(repository).toAbsolutePath().normalize();
        Path work = Files.createTempDirectory(root, ".training-work");
        try {
            return build(root, work);
        } finally {
            deleteRecursively(work);
        }
    }

    /**
     * The jars as {@code mvn dependency:list -DoutputAbsoluteArtifactFilename=true} lists them, compile scope.
     */
    public static String dependencyList(List<Artifact> artifacts) {
        return artifacts.stream()
                .map(artifact -> "   " + GROUP_ID + ":" + artifact.flavour().artifactId() + ":jar:" + VERSION
                        + ":compile:" + artifact.binaryJar())
                .collect(Collectors.joining("\n", "\nThe following files have been resolved:\n", "\n"));
    }

    private List<Artifact> build(Path repository, Path work) throws IOException {
        Path sourceRoot = work.resolve("src");
        Path classes = Files.createDirectories(work.resolve("classes"));
        Map<Flavour, Map<String, String>> sources = new TreeMap<>();
        List<Path> files = new ArrayList<>();
        for (Flavour flavour : Flavour.values()) {
            Map<String, String> texts = TrainingLibrarySources.forPackage(flavour.packageName());
            sources.put(flavour, texts);
            for (Map.Entry<String, String> source : texts.entrySet()) {
                files.add(write(sourceRoot, source.getKey(), source.getValue()));
            }
        }
        compile(files, classes);

        List<Artifact> artifacts = new ArrayList<>();
        for (Flavour flavour : Flavour.values()) {
            String packagePath = flavour.packageName().replace('.', '/');
            Path directory = Files.createDirectories(repository.resolve(GROUP_ID.replace('.', '/'))
                    .resolve(flavour.artifactId()).resolve(VERSION));
            String base = flavour.artifactId() + "-" + VERSION;
            Path binaryJar = writeJar(classes.resolve(packagePath), packagePath + "/", directory.resolve(base + ".jar"));
            Optional<Path> sourcesJar = Optional.empty();
            Optional<Path> javadocJar = Optional.empty();
            if (flavour == Flavour.SOURCES) {
                sourcesJar = Optional.of(writeJar(sourceRoot.resolve(packagePath), packagePath + "/",
                        directory.resolve(base + "-sources.jar")));
            } else if (flavour == Flavour.JAVADOC) {
                Path apidocs = work.resolve("apidocs-" + flavour.artifactId());
                javadoc(sources.get(flavour).keySet().stream().map(sourceRoot::resolve).toList(), sourceRoot, apidocs);
                javadocJar = Optional.of(writeJar(apidocs, "", directory.resolve(base + "-javadoc.jar")));
            }
            writePom(directory.resolve(base + ".pom"), flavour);
            artifacts.add(new Artifact(flavour, binaryJar, sourcesJar, javadocJar));
        }
        return List.copyOf(artifacts);
    }

    private void compile(List<Path> files, Path classes) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<String> options = List.of("-d", classes.toString(), "-encoding", "UTF-8", "-g", "-proc:none",
                    "-nowarn", "-Xlint:none");
            StringWriter out = new StringWriter();
            boolean ok = compiler.getTask(out, fileManager, diagnostics, options, null,
                    fileManager.getJavaFileObjectsFromPaths(files)).call();
            if (!ok) {
                throw new IllegalStateException("the training library does not compile: "
                        + diagnostics.getDiagnostics() + out);
            }
        }
    }

    private void javadoc(List<Path> files, Path sourceRoot, Path apidocs) throws IOException {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                     documentation.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<String> options = List.of("-d", apidocs.toString(), "-sourcepath", sourceRoot.toString(),
                    "-encoding", "UTF-8", "-docencoding", "UTF-8", "-charset", "UTF-8", "-notimestamp", "-quiet",
                    "-Xdoclint:none", "-protected", "-locale", "en");
            StringWriter out = new StringWriter();
            boolean ok = documentation.getTask(out, fileManager, diagnostics, null, options,
                    fileManager.getJavaFileObjectsFromPaths(files)).call();
            if (!ok) {
                throw new IllegalStateException("javadoc failed for the training library: "
                        + diagnostics.getDiagnostics() + out);
            }
        }
    }

    /** Writes every regular file below {@code root} into a jar, entry names prefixed, sorted, fixed timestamps. */
    private static Path writeJar(Path root, String prefix, Path jar) throws IOException {
        Map<String, Path> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                files.put(prefix + root.relativize(file).toString().replace('\\', '/'), file);
            }
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jarOut = new JarOutputStream(out)) {
            jarOut.putNextEntry(entry("META-INF/MANIFEST.MF"));
            manifest.write(jarOut);
            jarOut.closeEntry();
            for (Map.Entry<String, Path> file : files.entrySet()) {
                jarOut.putNextEntry(entry(file.getKey()));
                Files.copy(file.getValue(), jarOut);
                jarOut.closeEntry();
            }
        }
        return jar;
    }

    private static JarEntry entry(String name) {
        JarEntry entry = new JarEntry(name);
        entry.setLastModifiedTime(ENTRY_TIME);
        return entry;
    }

    private static void writePom(Path pom, Flavour flavour) throws IOException {
        Files.writeString(pom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(GROUP_ID, flavour.artifactId(), VERSION), StandardCharsets.UTF_8);
    }

    private static Path write(Path root, String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
