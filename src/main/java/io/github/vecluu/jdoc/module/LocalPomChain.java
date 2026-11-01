package io.github.vecluu.jdoc.module;

import java.util.List;
import java.util.Optional;

/**
 * A module POM followed by its local ancestors, i.e. the parents found on disk through {@code <relativePath>}
 * whose coordinates match the declaration.
 *
 * @param poms             the module POM first, then each local ancestor; never empty
 * @param repositoryParent the first parent that is not available locally (it comes from the repository), if any
 */
public record LocalPomChain(List<PomModel> poms, Optional<ParentReference> repositoryParent) {

    public LocalPomChain {
        poms = List.copyOf(poms);
        if (poms.isEmpty()) {
            throw new IllegalArgumentException("a POM chain contains at least the module POM");
        }
        repositoryParent = repositoryParent == null ? Optional.empty() : repositoryParent;
    }

    /** The module POM. */
    public PomModel module() {
        return poms.getFirst();
    }
}
