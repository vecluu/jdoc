package io.github.vecluu.jdoc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The code documentation lives next to the code, so its presence is checked here: every main package (and the shared
 * test fixtures in {@code testsupport}) has a {@code package-info.java} with a non-empty Javadoc comment, and every
 * public top-level type under {@code src/main/java} has a Javadoc comment. Reads only the file system; whether the
 * {@code {@link}}s in those comments resolve is checked by javac's doclint during the compile.
 */
class JavadocPresenceTest {

    private static final String BASE_PATH = "io/github/vecluu/jdoc";
    private static final Path MAIN = Path.of("src/main/java", BASE_PATH);
    private static final Path TEST_SUPPORT = Path.of("src/test/java", BASE_PATH, "testsupport");

    /** A public top-level type declaration at the start of a line (nested types are indented). */
    private static final Pattern PUBLIC_TOP_LEVEL_TYPE = Pattern.compile(
            "^public\\s+(?:(?:abstract|final|sealed|non-sealed|static|strictfp)\\s+)*"
                    + "(?:class|interface|enum|record|@interface)\\s+(\\w+)",
            Pattern.MULTILINE);
    private static final Pattern JAVADOC = Pattern.compile("/\\*\\*(.*?)\\*/", Pattern.DOTALL);

    @Test
    void everyPackageHasAPackageInfoWithJavadoc() throws IOException {
        Path root = projectRoot();
        List<Path> packages = new ArrayList<>();
        try (Stream<Path> directories = Files.walk(root.resolve(MAIN))) {
            directories.filter(Files::isDirectory)
                    .filter(directory -> !directory.equals(root.resolve(MAIN)))
                    .sorted()
                    .forEach(packages::add);
        }
        packages.add(root.resolve(TEST_SUPPORT));
        List<String> problems = new ArrayList<>();
        for (Path directory : packages) {
            Path packageInfo = directory.resolve("package-info.java");
            String where = root.relativize(packageInfo).toString();
            if (!Files.isRegularFile(packageInfo)) {
                problems.add(where + " is missing: say what the package is for and its package-wide rules");
            } else if (!hasText(leadingJavadoc(Files.readString(packageInfo, StandardCharsets.UTF_8), "package"))) {
                problems.add(where + " has no Javadoc comment (or an empty one) before its package declaration");
            }
        }
        assertThat(problems).as(report(problems)).isEmpty();
    }

    @Test
    void everyPublicTopLevelMainTypeHasJavadoc() throws IOException {
        Path root = projectRoot();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve(MAIN))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher type = PUBLIC_TOP_LEVEL_TYPE.matcher(text);
                while (type.find()) {
                    if (!hasText(javadocBefore(text, type.start()))) {
                        problems.add(root.relativize(file) + ": public type " + type.group(1)
                                + " has no Javadoc comment");
                    }
                }
            }
        }
        assertThat(problems).as(report(problems)).isEmpty();
    }

    /** The Javadoc comment directly before the first {@code keyword} line, if any. */
    private static Optional<String> leadingJavadoc(String text, String keyword) {
        Matcher declaration = Pattern.compile("^" + keyword + "\\s", Pattern.MULTILINE).matcher(text);
        return declaration.find() ? javadocBefore(text, declaration.start()) : Optional.empty();
    }

    /**
     * The body of the Javadoc comment that ends right before {@code offset}, with only annotation lines and whitespace
     * in between; empty if there is none.
     */
    private static Optional<String> javadocBefore(String text, int offset) {
        String before = text.substring(0, offset);
        String[] lines = before.split("\n", -1);
        int line = lines.length - 2; // the last element is the (empty) start of the declaration line
        while (line >= 0 && (lines[line].isBlank() || lines[line].strip().startsWith("@"))) {
            line--;
        }
        if (line < 0 || !lines[line].strip().endsWith("*/")) {
            return Optional.empty();
        }
        int end = before.lastIndexOf("*/");
        int start = before.lastIndexOf("/*", end);
        Matcher javadoc = JAVADOC.matcher(before.substring(Math.max(start, 0), end + 2));
        return start >= 0 && javadoc.matches() ? Optional.of(javadoc.group(1)) : Optional.empty();
    }

    private static boolean hasText(Optional<String> body) {
        return body.map(text -> text.replaceAll("[\\s*]", "")).filter(text -> !text.isEmpty()).isPresent();
    }

    private static Path projectRoot() {
        return Optional.ofNullable(System.getProperty("jdoc.projectRoot"))
                .map(Path::of)
                .orElseGet(() -> Path.of("").toAbsolutePath());
    }

    private static String report(List<String> problems) {
        return problems.size() + " documentation problem(s):\n  " + String.join("\n  ", problems);
    }
}
