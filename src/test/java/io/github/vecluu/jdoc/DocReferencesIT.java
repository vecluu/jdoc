package io.github.vecluu.jdoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Keeps the docs honest about names: a renamed class or a moved file that the docs do not follow fails the build, and
 * the message names the stale entry, its file and its line.
 *
 * <ul>
 *   <li><b>Types:</b> every inline code span in {@code docs/ARCHITECTURE.md} that reads as a type reference
 *       (grammar in {@link DocReferences#typeReference}: {@code pkg.Type}, {@code pkg.Type#member},
 *       {@code Type.Nested.member(args)}, a bare UpperCamel {@code Type}) resolves to a type declared in a source file
 *       under {@code src/main/java} or {@code src/test/java} (bare names also in the fixture library sources under
 *       {@code src/test/resources}): in that package if one is given, nested types declared in the same file as the
 *       type before them, and a member name appearing as a word in that file. Names in {@link #NOT_JDOC_TYPES} (JDK
 *       and library types, placeholders used as examples) are not checked.
 *   <li><b>Paths:</b> every inline code span that reads as a repository-relative path (rule in
 *       {@link DocReferences#pathReference}: starts with a top-level directory such as {@code docs/} or
 *       {@code src/}, or is a root file such as {@code pom.xml} or {@code CLAUDE.md}), and every relative Markdown link
 *       target, exists. Checked in that file plus {@code CLAUDE.md}, {@code docs/SPEC.md},
 *       {@code docs/RELEASING.md}, {@code docs/AGENT_GUIDE.md} and {@code docs/TASK_TEMPLATE.md}. Names in
 *       {@link #NOT_REPOSITORY_FILES} (files in the release bundle or the user's project, not in this repository)
 *       are not checked. Nothing under {@code tasks/} is checked: that directory is local and ignored by git.
 * </ul>
 *
 * <p>Fast and offline: it reads only the working tree. The project root is the failsafe system property
 * {@code jdoc.projectRoot}, else the working directory.
 */
class DocReferencesIT {

    /** JDK and library types and placeholder names the docs use in examples; never resolved against the sources. */
    static final Set<String> NOT_JDOC_TYPES = Set.of(
            // JDK and libraries
            "Object", "String", "Optional", "List", "Map", "Set", "Entry", "Duration", "Clock", "Supplier", "Path",
            "ReentrantLock", "FileChannel", "JavacTask", "DocTrees", "DocCommentTree", "ComparableVersion",
            "SQLException", "RuntimeException", "IllegalArgumentException", "Error", "OSInfo", "System",
            // the "never start a test class name with Test" rule
            "Test",
            // the sample project and examples of query syntax
            "StringUtils", "Greeter", "Type", "Foo", "Outer", "Inner", "MutableAnyPair");

    /** Root-level Markdown names that are not files of this repository (bundle or user-project files). */
    static final Set<String> NOT_REPOSITORY_FILES = Set.of("SKILL.md", "AGENTS.md", "AGENTS-snippet.md");

    /** The doc whose type references are checked. Paths are checked in it plus {@link #PATH_ONLY_DOCS}. */
    private static final String ARCHITECTURE = "docs/ARCHITECTURE.md";
    private static final List<String> PATH_ONLY_DOCS =
            List.of("CLAUDE.md", "docs/SPEC.md", "docs/RELEASING.md", "docs/AGENT_GUIDE.md", "docs/TASK_TEMPLATE.md");

    private static final String BASE_PATH = "io/github/vecluu/jdoc";
    private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");
    /** Java sources of the test fixture library: bare names resolve against them, packaged names never do. */
    private static final String FIXTURE_SOURCES = "src/test/resources";
    private static final String FIXTURE_PACKAGE = "(fixture)";
    private static final Pattern DECLARATION =
            Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Z]\\w*)");

    private static Path root;
    private static List<SourceFile> sources;
    private static Set<String> packages;

    /** One Java source file: its package below the base package ({@code ""} for the root), types and text. */
    private record SourceFile(Path path, String packageName, Set<String> declaredTypes, String text) {}

    @BeforeAll
    static void readSources() throws IOException {
        root = Optional.ofNullable(System.getProperty("jdoc.projectRoot"))
                .map(Path::of)
                .orElseGet(() -> Path.of("").toAbsolutePath());
        assertThat(root.resolve(ARCHITECTURE)).as("project root %s", root).isRegularFile();
        sources = new ArrayList<>();
        for (String sourceRoot : SOURCE_ROOTS) {
            Path base = root.resolve(sourceRoot).resolve(BASE_PATH);
            try (Stream<Path> files = Files.walk(base)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Path directory = base.relativize(file).getParent();
                    sources.add(sourceFile(file,
                            directory == null ? "" : directory.toString().replace('/', '.')));
                }
            }
        }
        try (Stream<Path> files = Files.walk(root.resolve(FIXTURE_SOURCES))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                sources.add(sourceFile(file, FIXTURE_PACKAGE));
            }
        }
        try (Stream<Path> directories = Files.list(root.resolve("src/main/java").resolve(BASE_PATH))) {
            packages = directories.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        Set<String> testPackages = sources.stream()
                .map(SourceFile::packageName)
                .filter(p -> !p.equals(FIXTURE_PACKAGE))
                .collect(Collectors.toSet());
        packages = Set.copyOf(union(packages, testPackages));
    }

    @Test
    void everyTypeNamedInTheDocsExists() {
        List<String> problems = new ArrayList<>();
        for (String doc : typeDocs()) {
            for (DocReferences.Span span : DocReferences.codeSpans(read(doc))) {
                DocReferences.typeReference(span.text(), packages)
                        .flatMap(reference -> unresolved(reference))
                        .ifPresent(why -> problems.add(
                                doc + ":" + span.line() + ": `" + span.text() + "` does not resolve: " + why));
            }
        }
        assertThat(problems).as(report(problems)).isEmpty();
    }

    @Test
    void everyPathNamedInTheDocsExists() {
        List<String> docs = new ArrayList<>(typeDocs());
        docs.addAll(PATH_ONLY_DOCS);
        List<String> problems = new ArrayList<>();
        for (String doc : docs) {
            String text = read(doc);
            for (DocReferences.Span span : DocReferences.codeSpans(text)) {
                DocReferences.pathReference(span.text())
                        .filter(path -> !NOT_REPOSITORY_FILES.contains(path))
                        .filter(path -> !Files.exists(root.resolve(path)))
                        .ifPresent(path -> problems.add(
                                doc + ":" + span.line() + ": `" + span.text() + "` names " + path
                                        + ", which does not exist in the repository"));
            }
            Path directory = root.resolve(doc).getParent();
            for (DocReferences.Span link : DocReferences.linkTargets(text)) {
                if (!Files.exists(directory.resolve(link.text()))) {
                    problems.add(doc + ":" + link.line() + ": link target " + link.text()
                            + " does not exist (relative to " + root.relativize(directory) + "/)");
                }
            }
        }
        assertThat(problems).as(report(problems)).isEmpty();
    }

    /** Why the reference does not resolve, or empty if it does. */
    private static Optional<String> unresolved(DocReferences.TypeRef reference) {
        String first = reference.types().getFirst();
        if (NOT_JDOC_TYPES.contains(first)) {
            return Optional.empty();
        }
        List<SourceFile> candidates = sources.stream()
                .filter(file -> reference.packageName().map(file.packageName()::equals).orElse(true))
                .filter(file -> file.declaredTypes().contains(first))
                .toList();
        String where = reference.packageName().map(p -> "package " + p).orElse("src/main or src/test");
        if (candidates.isEmpty()) {
            return Optional.of("no type " + first + " in " + where);
        }
        for (String nested : reference.types().subList(1, reference.types().size())) {
            List<SourceFile> declaring = candidates.stream()
                    .filter(file -> file.declaredTypes().contains(nested))
                    .toList();
            if (declaring.isEmpty()) {
                return Optional.of("no nested type " + nested + " in " + names(candidates));
            }
            candidates = declaring;
        }
        if (reference.member().isPresent()) {
            Pattern word = Pattern.compile("\\b" + Pattern.quote(reference.member().get()) + "\\b");
            if (candidates.stream().noneMatch(file -> word.matcher(file.text()).find())) {
                return Optional.of("no member " + reference.member().get() + " in " + names(candidates));
            }
        }
        return Optional.empty();
    }

    private static SourceFile sourceFile(Path file, String packageName) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Set<String> declared = new HashSet<>();
        String fileName = file.getFileName().toString();
        declared.add(fileName.substring(0, fileName.length() - ".java".length()));
        Matcher matcher = DECLARATION.matcher(text);
        while (matcher.find()) {
            declared.add(matcher.group(1));
        }
        return new SourceFile(root.relativize(file), packageName, Set.copyOf(declared), text);
    }

    private static List<String> typeDocs() {
        return List.of(ARCHITECTURE);
    }

    private static String read(String doc) {
        try {
            return Files.readString(root.resolve(doc), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String names(List<SourceFile> files) {
        return files.stream().map(f -> f.path().toString()).sorted().collect(Collectors.joining(", "));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new TreeSet<>(a);
        all.addAll(b);
        all.remove("");
        return all;
    }

    private static String report(List<String> problems) {
        return problems.size() + " stale doc reference(s); fix the doc (or the allow-list in DocReferencesIT):\n  "
                + String.join("\n  ", problems);
    }
}
