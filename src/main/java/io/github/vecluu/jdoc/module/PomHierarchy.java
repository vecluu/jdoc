package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.error.ResolutionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Follows {@code <parent>} declarations to POMs on disk. */
@Slf4j
@RequiredArgsConstructor
public final class PomHierarchy {

    private final PomReader reader;

    /**
     * Reads a module POM and its local ancestors.
     *
     * @throws ResolutionException if the module POM itself cannot be read (unreadable ancestors just end the chain)
     */
    public LocalPomChain localChain(Path modulePom) {
        PomModel current = reader.read(modulePom);
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
        return pom.parent().flatMap(parent -> parent.localPomCandidate(pom.directory())
                .filter(Files::isRegularFile)
                .flatMap(this::tryRead)
                .filter(candidate -> candidate.hasCoordinates(parent.gav())));
    }

    /** Reads a POM, returning empty (and logging at debug) if it cannot be read. */
    public Optional<PomModel> tryRead(Path pomFile) {
        try {
            return Optional.of(reader.read(pomFile));
        } catch (ResolutionException e) {
            log.debug("ignoring unreadable POM: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
