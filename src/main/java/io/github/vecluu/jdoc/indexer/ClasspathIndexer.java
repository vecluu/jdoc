package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.config.JdkInfo;
import io.github.vecluu.jdoc.error.JdocException;
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
        List<Target> pending = new ArrayList<>();
        for (Target target : targets) {
            Optional<IndexedArtifact> indexed = store.find(target.key());
            if (target.needsIndexing(indexed)) {
                pending.add(target);
            } else {
                inScope.put(target.key(), indexed.orElseThrow());
            }
        }
        int reused = inScope.size();
        int indexedCount = 0;
        int types = 0;
        int members = 0;
        List<String> failures = new ArrayList<>();
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
                    try {
                        StoredArtifact stored = indexOne(target, indexed);
                        inScope.put(target.key(), stored.artifact());
                        indexedCount++;
                        types += stored.typeCount();
                        members += stored.memberCount();
                    } catch (JdocException e) {
                        log.warn("cannot index {}: {}", target.key().label(), e.getMessage());
                        failures.add(target.key().label() + ": " + e.getMessage());
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
