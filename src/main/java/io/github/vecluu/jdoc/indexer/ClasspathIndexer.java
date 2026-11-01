package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.config.JdkInfo;
import io.github.vecluu.jdoc.error.JdocException;
import io.github.vecluu.jdoc.index.FailedArtifact;
import io.github.vecluu.jdoc.index.IndexLock;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.ModuleClasspath;
import io.github.vecluu.jdoc.model.ResolvedDependency;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Entry point of indexing: makes sure the JDK and every jar of a module's classpath are indexed and current.
 *
 * <p>Refresh rules: each jar is stat'ed; a jar that is new, or whose path, mtime or size differ from the indexed
 * values, is re-indexed alone; unchanged jars are reused. {@link IndexOptions#force()} re-indexes every artifact of
 * the module (not the JDK, see {@link IndexOptions#forceJdk()}). The {@link IndexLock} is taken only when something
 * needs indexing; after acquiring it the need is checked again, because another process may have done the work
 * while this one waited. Jars missing on disk are skipped (debug log). Re-indexing an artifact drops its cached
 * decompiled sources. An artifact that fails to index is reported and skipped, never failing the call.
 *
 * <p>A failure is remembered in the store with the jar's path, mtime and size. While the jar stays unchanged, later
 * calls do not retry it (and so do not take the lock for it); they report it as previously failed. A changed jar or a
 * forced run retries it; a successful index forgets the failure.
 *
 * <p>Like the {@link IndexStore} it uses, an instance must be confined to one thread at a time.
 */
@Slf4j
@RequiredArgsConstructor
public final class ClasspathIndexer {

    private final IndexStore store;
    private final ArtifactIndexer artifactIndexer;
    private final JdkIndexer jdkIndexer;
    private final JdkInfo jdk;
    private final IndexLock lock;
    private final FileStatter statter;
    private final DecompiledCacheEvictor evictor;

    /** One artifact to keep current. */
    private record Target(ArtifactKey key, Optional<Gav> gav, Path file, FileStamp stamp, boolean forced) {

        boolean isCurrent(IndexedArtifact indexed) {
            return indexed.jarPath().equals(file) && indexed.jarMtime() == stamp.mtimeMillis()
                    && indexed.jarSize() == stamp.size();
        }

        boolean needsIndexing(Optional<IndexedArtifact> indexed) {
            return forced || indexed.filter(this::isCurrent).isEmpty();
        }
    }

    /**
     * Indexes what is missing or stale.
     *
     * @throws io.github.vecluu.jdoc.error.IndexException if the lock cannot be acquired or the store cannot be read
     */
    public IndexResult index(ModuleClasspath classpath, IndexOptions options) {
        List<Target> targets = targets(classpath, options);
        Map<ArtifactKey, IndexedArtifact> inScope = new LinkedHashMap<>();
        List<IndexFailure> failures = new ArrayList<>();
        List<Target> pending = new ArrayList<>();
        for (Target target : targets) {
            Optional<IndexedArtifact> indexed = store.find(target.key());
            if (!target.needsIndexing(indexed)) {
                inScope.put(target.key(), indexed.orElseThrow());
            } else if (!skipPreviouslyFailed(target, indexed, inScope, failures)) {
                pending.add(target);
            }
        }
        int reused = inScope.size();
        int indexedCount = 0;
        int types = 0;
        int members = 0;
        if (!pending.isEmpty()) {
            try (IndexLock.Lease lease = lock.acquire()) {
                for (Target target : pending) {
                    Optional<IndexedArtifact> indexed = store.find(target.key());
                    if (!target.needsIndexing(indexed)) {
                        log.debug("{} was indexed by another process meanwhile", target.key().label());
                        inScope.put(target.key(), indexed.orElseThrow());
                        reused++;
                        continue;
                    }
                    if (skipPreviouslyFailed(target, indexed, inScope, failures)) {
                        continue;
                    }
                    try {
                        StoredArtifact stored = indexOne(target, indexed);
                        inScope.put(target.key(), stored.artifact());
                        indexedCount++;
                        types += stored.typeCount();
                        members += stored.memberCount();
                    } catch (JdocException e) {
                        log.warn("cannot index {}: {}", target.key().label(), e.getMessage());
                        IndexFailure failure = new IndexFailure(target.key(), e.getMessage(), false);
                        failures.add(failure);
                        remember(target, failure);
                        indexed.ifPresent(stale -> inScope.put(target.key(), stale));
                    }
                }
            }
        }
        IndexedClasspath indexedClasspath = indexedClasspath(classpath, targets, inScope);
        List<ArtifactWithoutSources> missing = options.reportMissingSources()
                ? missingSources(targets, inScope)
                : List.of();
        return new IndexResult(indexedClasspath,
                new IndexReport(indexedCount, reused, types, members, missing, failures));
    }

    /**
     * Skips a target whose jar, unchanged, already failed to index in an earlier call (unless forced): reports it as
     * previously failed and keeps an older index of it in scope.
     *
     * @return true if the target is skipped
     */
    private boolean skipPreviouslyFailed(Target target, Optional<IndexedArtifact> indexed,
                                         Map<ArtifactKey, IndexedArtifact> inScope, List<IndexFailure> failures) {
        if (target.forced()) {
            return false;
        }
        Optional<FailedArtifact> failed = store.findFailure(target.key()).filter(failure -> failure.matches(
                target.file(), target.stamp().mtimeMillis(), target.stamp().size()));
        if (failed.isEmpty()) {
            return false;
        }
        log.warn("skipping {}: it failed to index before ({}); retried when its file changes or on a forced index",
                target.key().label(), failed.get().reason());
        failures.add(new IndexFailure(target.key(), failed.get().reason(), true));
        indexed.ifPresent(stale -> inScope.put(target.key(), stale));
        return true;
    }

    /** Records a failure so that later calls skip the unchanged jar; a store error here only costs a retry. */
    private void remember(Target target, IndexFailure failure) {
        try {
            store.recordFailure(new FailedArtifact(target.key(), target.file(), target.stamp().mtimeMillis(),
                    target.stamp().size(), failure.reason()));
        } catch (JdocException e) {
            log.warn("cannot remember that {} failed to index: {}", target.key().label(), e.getMessage());
        }
    }

    private List<Target> targets(ModuleClasspath classpath, IndexOptions options) {
        List<Target> targets = new ArrayList<>();
        Set<ArtifactKey> seen = new LinkedHashSet<>();
        for (ResolvedDependency dependency : classpath.dependencies()) {
            ArtifactKey key = dependency.artifactKey();
            if (!seen.add(key)) {
                continue;
            }
            Optional<FileStamp> stamp = statter.stat(dependency.jar());
            if (stamp.isEmpty()) {
                log.debug("skipping {}: jar {} does not exist", key.label(), dependency.jar());
                continue;
            }
            targets.add(new Target(key, Optional.of(dependency.gav()), dependency.jar(), stamp.get(),
                    options.force()));
        }
        Path jdkFile = jdkIndexer.indexedFile(jdk);
        Optional<FileStamp> jdkStamp = statter.stat(jdkFile);
        if (jdkStamp.isPresent()) {
            targets.add(new Target(jdk.artifactKey(), Optional.empty(), jdkFile, jdkStamp.get(), options.forceJdk()));
        } else {
            log.warn("cannot index the JDK: {} does not exist", jdkFile);
        }
        return targets;
    }

    private StoredArtifact indexOne(Target target, Optional<IndexedArtifact> previous) {
        if (target.gav().isEmpty()) {
            return jdkIndexer.index(jdk, target.stamp());
        }
        Gav gav = target.gav().get();
        if (previous.isPresent()) {
            evictor.evict(gav);
        }
        return artifactIndexer.index(gav, target.file(), target.stamp());
    }

    private static IndexedClasspath indexedClasspath(ModuleClasspath classpath, List<Target> targets,
                                                     Map<ArtifactKey, IndexedArtifact> inScope) {
        List<Long> ids = new ArrayList<>();
        Map<Long, ArtifactKey> keys = new LinkedHashMap<>();
        Optional<Long> jdkId = Optional.empty();
        for (Target target : targets) {
            IndexedArtifact artifact = inScope.get(target.key());
            if (artifact == null) {
                continue;
            }
            ids.add(artifact.id());
            keys.put(artifact.id(), target.key());
            if (target.key().isJdk()) {
                jdkId = Optional.of(artifact.id());
            }
        }
        return new IndexedClasspath(ids, keys, jdkId, classpath.status(), classpath.warnings());
    }

    private List<ArtifactWithoutSources> missingSources(List<Target> targets,
                                                        Map<ArtifactKey, IndexedArtifact> inScope) {
        List<ArtifactWithoutSources> result = new ArrayList<>();
        for (Target target : targets) {
            IndexedArtifact artifact = inScope.get(target.key());
            if (artifact == null || target.key().isJdk()) {
                continue;
            }
            List<TypeWithoutSources> types = store.typesWithoutSources(artifact.id()).stream()
                    .map(IndexedType::descriptor)
                    .filter(type -> type.visibility().isApi())
                    .sorted(Comparator.comparing(TypeDescriptor::name))
                    .map(type -> new TypeWithoutSources(type.name(), type.docSource()))
                    .toList();
            if (!types.isEmpty()) {
                result.add(new ArtifactWithoutSources(target.key(), types));
            }
        }
        return result;
    }
}
