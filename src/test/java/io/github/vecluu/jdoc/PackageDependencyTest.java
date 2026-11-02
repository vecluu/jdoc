package io.github.vecluu.jdoc;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The package layering of the main code, and the only place it is written down: {@link #ALLOWED} names, for every
 * package below {@code io.github.vecluu.jdoc}, the other jdoc packages its classes may import. Dependencies point down
 * a fixed layering; only the classes in {@link #IMPORTS_ANYTHING} (the composition root) may import every package.
 *
 * <p>Reads the {@code import} lines (static ones too) of every file under {@code src/main/java}. A new package needs an
 * entry here; a new edge needs a deliberate change of its row.
 */
class PackageDependencyTest {

    /** The root package, which holds only {@code Main}. */
    private static final String ROOT = "";

    /** Package → the jdoc packages it may import. The single source of the layering rule. */
    static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            entry("model", Set.of()),
            entry("error", Set.of()),
            entry("config", Set.of("model", "error")),
            entry("query", Set.of("model")),
            entry("text", Set.of("model")),
            entry("module", Set.of("config", "model", "error")),
            entry("index", Set.of("config", "model", "error")),
            entry("javadoc", Set.of("model", "error")),
            entry("source", Set.of("model", "javadoc", "error")),
            entry("bytecode", Set.of("model", "error")),
            entry("decompile", Set.of("config", "model", "error")),
            entry("indexer", Set.of("model", "error", "config", "module", "index", "javadoc", "source", "bytecode",
                    "decompile")),
            entry("lookup", Set.of("index", "source", "decompile", "query", "model", "error")),
            entry("app", Set.of("model", "error", "config", "query", "text", "module", "index", "javadoc", "source",
                    "bytecode", "decompile", "indexer", "lookup")),
            entry("cli", Set.of("app", "config", "error", "text")),
            entry(ROOT, Set.of("cli", "config")));

    /** Classes (package-qualified) that may import any jdoc package: the one place concrete classes are wired. */
    static final Set<String> IMPORTS_ANYTHING = Set.of("cli.CompositionRoot");

    private static final String BASE_PACKAGE = "io.github.vecluu.jdoc";
    private static final Pattern JDOC_IMPORT = Pattern.compile(
            "^import\\s+(?:static\\s+)?" + Pattern.quote(BASE_PACKAGE) + "\\.([a-z]\\w*)\\.", Pattern.MULTILINE);

    @Test
    void everyMainPackageHasARule() throws IOException {
        Set<String> packages = mainSources().stream()
                .map(MainSource::packageName)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> withoutRule = new TreeSet<>(packages);
        withoutRule.removeAll(ALLOWED.keySet());
        Set<String> ruleWithoutPackage = new TreeSet<>(ALLOWED.keySet());
        ruleWithoutPackage.removeAll(packages);
        assertThat(withoutRule)
                .as("main packages without an entry in PackageDependencyTest.ALLOWED: %s", withoutRule)
                .isEmpty();
        assertThat(ruleWithoutPackage)
                .as("PackageDependencyTest.ALLOWED names packages that do not exist: %s", ruleWithoutPackage)
                .isEmpty();
    }

    @Test
    void everyImportFollowsTheLayering() throws IOException {
        List<String> problems = new ArrayList<>();
        for (MainSource source : mainSources()) {
            if (IMPORTS_ANYTHING.contains(source.className())) {
                continue;
            }
            Set<String> allowed = ALLOWED.getOrDefault(source.packageName(), Set.of());
            Matcher matcher = JDOC_IMPORT.matcher(source.text());
            Set<String> forbidden = new TreeSet<>();
            while (matcher.find()) {
                String imported = matcher.group(1);
                if (!imported.equals(source.packageName()) && !allowed.contains(imported)) {
                    forbidden.add(imported);
                }
            }
            for (String imported : forbidden) {
                problems.add("`" + source.className() + "` imports `" + imported + "`, allowed: "
                        + (allowed.isEmpty() ? "nothing" : String.join(", ", new TreeSet<>(allowed))));
            }
        }
        assertThat(problems)
                .as("%d import(s) break the package layering; change the code, or the rule in "
                        + "PackageDependencyTest.ALLOWED if the new edge is intended:%n  %s",
                        problems.size(), String.join("\n  ", problems))
                .isEmpty();
    }

    /** A main source file: its package below the base package ({@link #ROOT} for the root) and its text. */
    private record MainSource(String packageName, String fileName, String text) {

        /** {@code pkg.Type}, or {@code Type} in the root package. */
        String className() {
            String type = fileName.substring(0, fileName.length() - ".java".length());
            return packageName.isEmpty() ? type : packageName + "." + type;
        }
    }

    private static List<MainSource> mainSources() throws IOException {
        Path base = projectRoot().resolve("src/main/java").resolve(BASE_PACKAGE.replace('.', '/'));
        List<MainSource> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(base)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                Path directory = base.relativize(file).getParent();
                String packageName = directory == null ? ROOT : directory.toString().replace('/', '.');
                sources.add(new MainSource(packageName, file.getFileName().toString(),
                        Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        return sources;
    }

    private static Path projectRoot() {
        return Optional.ofNullable(System.getProperty("jdoc.projectRoot"))
                .map(Path::of)
                .orElseGet(() -> Path.of("").toAbsolutePath());
    }
}
