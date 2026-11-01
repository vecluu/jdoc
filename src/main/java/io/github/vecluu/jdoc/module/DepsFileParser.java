package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.Scope;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses the {@code deps.txt} written by {@code mvn dependency:list -DoutputAbsoluteArtifactFilename=true}
 * (maven-dependency-plugin 3.x). Each dependency line looks like
 *
 * <pre>
 *    group:artifact:type[:classifier]:version:scope:/absolute/path.jar[ (optional)][ -- module name[ (auto)|[auto]]]
 * </pre>
 *
 * <p>Header lines, {@code none}, blank lines and anything unparsable are skipped, as are non-jar types (e.g.
 * {@code pom}), lines without an absolute path and entries whose file does not exist.
 */
@Slf4j
public final class DepsFileParser {

    private static final String MODULE_SUFFIX = " -- module";
    private static final String OPTIONAL_SUFFIX = "(optional)";

    private final Predicate<Path> fileExists;

    /** A parser that checks jar existence on the real filesystem. */
    public DepsFileParser() {
        this(Files::isRegularFile);
    }

    /** @param fileExists decides whether a listed jar exists */
    public DepsFileParser(Predicate<Path> fileExists) {
        this.fileExists = Objects.requireNonNull(fileExists, "fileExists must not be null");
    }

    /** Parses a whole {@code deps.txt}, keeping Maven's order. */
    public List<ResolvedDependency> parse(String content) {
        List<ResolvedDependency> dependencies = new ArrayList<>();
        content.lines().forEach(line -> parseLine(line).ifPresent(dependencies::add));
        return List.copyOf(dependencies);
    }

    /** Parses one line; empty for anything that is not a usable jar dependency. */
    public Optional<ResolvedDependency> parseLine(String line) {
        String text = line.strip();
        int moduleSuffix = text.indexOf(MODULE_SUFFIX);
        if (moduleSuffix >= 0) {
            text = text.substring(0, moduleSuffix).strip();
        }
        if (text.endsWith(OPTIONAL_SUFFIX)) {
            text = text.substring(0, text.length() - OPTIONAL_SUFFIX.length()).strip();
        }
        int pathStart = text.indexOf(":/");
        if (pathStart < 0) {
            return Optional.empty();
        }
        String[] fields = text.substring(0, pathStart).split(":", -1);
        String pathText = text.substring(pathStart + 1).strip();
        Optional<Gav> gav = gav(fields);
        Optional<Scope> scope = Scope.parse(fields[fields.length - 1]);
        if (gav.isEmpty() || scope.isEmpty()) {
            log.debug("skipping unparsable deps.txt line: {}", line);
            return Optional.empty();
        }
        if (!Gav.JAR.equals(gav.get().extension())) {
            return Optional.empty();
        }
        Optional<Path> jar = path(pathText);
        if (jar.isEmpty() || !fileExists.test(jar.get())) {
            log.debug("skipping {}: jar not found at {}", gav.get(), pathText);
            return Optional.empty();
        }
        return Optional.of(new ResolvedDependency(gav.get(), scope.get(), jar.get()));
    }

    private static Optional<Gav> gav(String[] fields) {
        try {
            return switch (fields.length) {
                case 5 -> Optional.of(new Gav(fields[0], fields[1], fields[3], Optional.empty(), fields[2]));
                case 6 -> Optional.of(new Gav(fields[0], fields[1], fields[4], Optional.of(fields[3]), fields[2]));
                default -> Optional.empty();
            };
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> path(String text) {
        try {
            Path path = Path.of(text);
            return path.isAbsolute() ? Optional.of(path.normalize()) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
