package io.github.vecluu.jdoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure parsing for {@link CodeMapIT}: finds the inline code spans of a Markdown file and decides which of them name a
 * Java type or a repository file. The rules are documented on {@link #typeReference} and {@link #pathReference}.
 */
final class CodeMapReferences {

    /** The base package; a span may start with it and is then read without it. */
    static final String BASE_PACKAGE = "io.github.vecluu.jdoc.";

    /**
     * Top-level directories of the repository; a path span must start with one of them. Not {@code tasks/}: it holds
     * the orchestrator's local files and is ignored by git, so it never exists in a clean checkout.
     */
    static final Set<String> ROOT_DIRECTORIES =
            Set.of("src", "docs", "dist", "scripts", "it", ".github", ".mvn");

    /** Files at the repository root that a span may name without a directory (besides upper-case {@code *.md}). */
    static final Set<String> ROOT_FILES = Set.of("pom.xml", "mvnw");

    /** File extensions: a dotted span ending in one of these is a file name, never a type reference. */
    private static final Set<String> FILE_EXTENSIONS = Set.of(
            "java", "class", "jar", "md", "xml", "sh", "txt", "json", "html", "zip", "db", "properties", "yml",
            "yaml", "gz", "aot", "jsa", "kt", "scala");

    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)");
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
    private static final Pattern TYPE_SPAN = Pattern.compile(
            "(?<path>[A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)(?:#(?<member>[\\w<>]+))?(?:\\(.*\\))?");
    private static final Pattern UPPER_CAMEL = Pattern.compile("[A-Z][A-Za-z0-9]*[a-z][A-Za-z0-9]*");
    private static final Pattern PATH_CHARACTERS = Pattern.compile("[\\w./-]+");
    private static final Pattern UPPER_CASE_MARKDOWN = Pattern.compile("[A-Z][A-Z0-9_-]*\\.md");

    private CodeMapReferences() {}

    /** An inline code span (whitespace runs collapsed to one space) and the 1-based line where it starts. */
    record Span(int line, String text) {}

    /**
     * A type reference: an optional jdoc package, the type and its nesting chain ({@code Outer}, {@code Inner}) and an
     * optional member name.
     */
    record TypeRef(Optional<String> packageName, List<String> types, Optional<String> member) {}

    /**
     * The inline code spans of {@code markdown}, outside fenced code blocks. A span may wrap onto the next line, as
     * Markdown allows.
     */
    static List<Span> codeSpans(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder text = new StringBuilder();
        List<Integer> lineStarts = new ArrayList<>();
        boolean fenced = false;
        for (String line : lines) {
            lineStarts.add(text.length());
            if (FENCE.matcher(line).find()) {
                fenced = !fenced;
                text.append('\n');
            } else {
                text.append(fenced ? "" : line).append('\n');
            }
        }
        List<Span> spans = new ArrayList<>();
        Matcher matcher = CODE_SPAN.matcher(text);
        while (matcher.find()) {
            String content = matcher.group(1).replaceAll("\\s+", " ").strip();
            if (!content.isEmpty()) {
                spans.add(new Span(lineOf(lineStarts, matcher.start()), content));
            }
        }
        return spans;
    }

    /** The relative targets of Markdown links ({@code [text](target)}), without an {@code #anchor}. */
    static List<Span> linkTargets(String markdown) {
        String[] lines = markdown.split("\n", -1);
        List<Span> targets = new ArrayList<>();
        boolean fenced = false;
        for (int i = 0; i < lines.length; i++) {
            if (FENCE.matcher(lines[i]).find()) {
                fenced = !fenced;
                continue;
            }
            if (fenced) {
                continue;
            }
            Matcher matcher = MARKDOWN_LINK.matcher(lines[i]);
            while (matcher.find()) {
                String target = matcher.group(1);
                int anchor = target.indexOf('#');
                String path = anchor < 0 ? target : target.substring(0, anchor);
                if (!path.isEmpty() && !path.contains(":")) {
                    targets.add(new Span(i + 1, path));
                }
            }
        }
        return targets;
    }

    /**
     * The type a span names, if it names one. The span, optionally prefixed with the base package, must be one of
     * <ul>
     *   <li>{@code pkg.Type}, {@code pkg.Type.Nested}, where {@code pkg} is a jdoc package (a directory under the base
     *       package; nested packages are not used);
     *   <li>{@code Type}, {@code Type.Nested} with no package, where {@code Type} is UpperCamel: an upper-case first
     *       letter and at least one lower-case letter ({@code Gav}, {@code JdocCli}; not {@code LICENSE},
     *       {@code SOURCES});
     * </ul>
     * either form optionally followed by one member, written {@code #member} or {@code .member} (lower-case or
     * {@code UPPER_CASE}), and optionally by a parenthesised argument list that is not checked. Anything else, such
     * as text with spaces, generics, a {@code $}, a hyphen, a lower-case first segment that is no jdoc package
     * ({@code java.util.Optional}, {@code jdoc.aotTraining}) or a file name ({@code Outer.java}), is not a reference.
     */
    static Optional<TypeRef> typeReference(String span, Set<String> packages) {
        String text = span.startsWith(BASE_PACKAGE) ? span.substring(BASE_PACKAGE.length()) : span;
        Matcher matcher = TYPE_SPAN.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        List<String> segments = List.of(matcher.group("path").split("\\."));
        Optional<String> member = Optional.ofNullable(matcher.group("member"));
        if (FILE_EXTENSIONS.contains(segments.getLast()) && segments.size() > 1 && member.isEmpty()) {
            return Optional.empty();
        }
        int index = 0;
        Optional<String> packageName = Optional.empty();
        if (packages.contains(segments.getFirst())) {
            packageName = Optional.of(segments.getFirst());
            index = 1;
        }
        List<String> types = new ArrayList<>();
        while (index < segments.size() && UPPER_CAMEL.matcher(segments.get(index)).matches()) {
            types.add(segments.get(index++));
        }
        if (types.isEmpty()) {
            return Optional.empty();
        }
        int rest = segments.size() - index;
        if (rest > 1 || (rest == 1 && member.isPresent())) {
            return Optional.empty();
        }
        if (rest == 1) {
            member = Optional.of(segments.get(index));
        }
        if (member.isPresent() && !member.get().matches("\\w+")) {
            member = Optional.empty();
        }
        return Optional.of(new TypeRef(packageName, List.copyOf(types), member));
    }

    /**
     * The repository-relative path a span names, if it names one. After a leading {@code ./} is dropped, the span's
     * first word (later words are arguments, as in {@code scripts/assemble.sh <version>}) must
     * <ul>
     *   <li>start with a top-level directory from {@link #ROOT_DIRECTORIES} followed by {@code /}, or
     *   <li>be a root file without a directory: {@link #ROOT_FILES} or an upper-case Markdown name such as
     *       {@code CLAUDE.md};
     * </ul>
     * and consist only of letters, digits, {@code _ . / -}. Placeholders and globs ({@code <package>}, {@code *},
     * {@code ...}) make a span a pattern, not a path, so it is not checked. A trailing {@code /} is dropped.
     */
    static Optional<String> pathReference(String span) {
        String text = span.startsWith("./") ? span.substring(2) : span;
        int space = text.indexOf(' ');
        String word = space < 0 ? text : text.substring(0, space);
        if (!PATH_CHARACTERS.matcher(word).matches() || word.contains("...") || word.contains("//")) {
            return Optional.empty();
        }
        String path = word.endsWith("/") ? word.substring(0, word.length() - 1) : word;
        int slash = path.indexOf('/');
        boolean underRootDirectory = slash > 0 && ROOT_DIRECTORIES.contains(path.substring(0, slash));
        boolean rootFile = slash < 0
                && (ROOT_FILES.contains(path) || UPPER_CASE_MARKDOWN.matcher(path).matches()
                        || ROOT_DIRECTORIES.contains(path) && word.endsWith("/"));
        return underRootDirectory || rootFile ? Optional.of(path) : Optional.empty();
    }

    private static int lineOf(List<Integer> lineStarts, int offset) {
        int line = 0;
        while (line + 1 < lineStarts.size() && lineStarts.get(line + 1) <= offset) {
            line++;
        }
        return line + 1;
    }
}
