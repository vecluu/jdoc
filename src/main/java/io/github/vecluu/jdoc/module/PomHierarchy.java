package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.error.ResolutionException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Follows {@code <parent>} declarations to POMs on disk. All file access of POM analysis goes through its
 * {@link #files()}, so that the inputs of an analysis can be recorded.
 */
@Slf4j
public final class PomHierarchy {

    private final PomReader reader;
    private final PomFiles files;

    /** A hierarchy on the default file system. */
    public PomHierarchy(PomReader reader) {
        this(reader, new NioPomFiles());
    }

    /** A hierarchy reading POMs through {@code files}. */
    public PomHierarchy(PomReader reader, PomFiles files) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.files = Objects.requireNonNull(files, "files");
    }

    /** The file access every POM analysis component must use. */
    public PomFiles files() {
        return files;
    }

    /**
     * Reads a POM through {@link #files()}.
     *
     * @throws ResolutionException if the file cannot be read or is not a well-formed POM
     */
    public PomModel read(Path pomFile) {
        byte[] content;
        try {
            content = files.read(pomFile);
        } catch (IOException e) {
            throw new ResolutionException("cannot read " + pomFile + ": " + e.getMessage(), e);
        }
        return reader.read(pomFile, content);
    }

    /**
     * Reads a module POM and its local ancestors.
     *
     * @throws ResolutionException if the module POM itself cannot be read (unreadable ancestors just end the chain)
     */
    public LocalPomChain localChain(Path modulePom) {
        PomModel current = read(modulePom);
        List<PomModel> poms = new ArrayList<>();
        Set<Path> visited = new HashSet<>();
        poms.add(current);
        visited.add(current.file());
        while (true) {
            Optional<ParentReference> parent = current.parent();
            if (parent.isEmpty()) {
                return new LocalPomChain(poms, Optional.empty());
            }
            Optional<PomModel> local = localParent(current).filter(model -> visited.add(model.file()));
            if (local.isEmpty()) {
                return new LocalPomChain(poms, parent);
            }
            current = local.get();
            poms.add(current);
        }
    }

    /**
     * The parent of {@code pom} if it is on disk at its {@code relativePath} and has the declared coordinates.
     */
    public Optional<PomModel> localParent(PomModel pom) {
        return pom.parent().flatMap(parent -> parent.localPomCandidate(pom.directory(), files::isDirectory)
                .filter(files::isRegularFile)
                .flatMap(this::tryRead)
                .filter(candidate -> candidate.hasCoordinates(parent.gav())));
    }

    /** Reads a POM, returning empty (and logging at debug) if it cannot be read. */
    public Optional<PomModel> tryRead(Path pomFile) {
        try {
            return Optional.of(read(pomFile));
        } catch (ResolutionException e) {
            log.debug("ignoring unreadable POM: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
