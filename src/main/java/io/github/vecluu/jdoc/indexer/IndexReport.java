package io.github.vecluu.jdoc.indexer;

import java.util.List;

/**
 * What one {@link ClasspathIndexer#index} call did. Counts include the JDK.
 *
 * @param artifactsIndexed artifacts (re-)indexed by this call
 * @param artifactsReused  artifacts that were already current
 * @param typesIndexed     types written by this call
 * @param membersIndexed   members written by this call
 * @param missingSources   per artifact (classpath order, JDK excluded), API types without a source file; empty
 *                         unless requested with {@link IndexOptions#reportMissingSources()}
 * @param failures         artifacts that could not be indexed, now or (unchanged jar) in an earlier call; each is
 *                         left out of scope unless an older index of it exists
 */
public record IndexReport(
        int artifactsIndexed,
        int artifactsReused,
        int typesIndexed,
        int membersIndexed,
        List<ArtifactWithoutSources> missingSources,
        List<IndexFailure> failures) {

    public IndexReport {
        missingSources = missingSources == null ? List.of() : List.copyOf(missingSources);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
