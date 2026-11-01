package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.model.Gav;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The {@code <parent>} element of a POM.
 *
 * @param gav          declared parent coordinates
 * @param relativePath where a local copy of the parent may live; empty when the POM declares an empty
 *                     {@code <relativePath/>} (no local parent). Defaults to {@code ../pom.xml}.
 */
public record ParentReference(Gav gav, Optional<String> relativePath) {

    /** Maven's default {@code relativePath}. */
    public static final String DEFAULT_RELATIVE_PATH = "../pom.xml";

    public ParentReference {
        Objects.requireNonNull(gav, "gav must not be null");
        relativePath = relativePath == null ? Optional.empty() : relativePath.map(String::strip);
    }

    /**
     * The local POM file the relative path points to (a directory means its {@code pom.xml}), if any. The file is
     * not checked for existence beyond the directory test.
     *
     * @param pomDirectory directory of the POM that declares this parent
     */
    public Optional<Path> localPomCandidate(Path pomDirectory) {
        return localPomCandidate(pomDirectory, Files::isDirectory);
    }

    /**
     * {@link #localPomCandidate(Path)} with the directory test supplied by the caller.
     *
     * @param pomDirectory directory of the POM that declares this parent
     * @param isDirectory  tells whether a path is a directory
     */
    public Optional<Path> localPomCandidate(Path pomDirectory, Predicate<Path> isDirectory) {
        return relativePath.filter(path -> !path.isEmpty()).map(path -> {
            Path candidate = pomDirectory.resolve(path).normalize();
            return isDirectory.test(candidate) ? candidate.resolve("pom.xml") : candidate;
        });
    }
}
