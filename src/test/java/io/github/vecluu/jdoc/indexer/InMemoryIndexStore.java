package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** {@link IndexStore} fake keeping artifacts and their entries in maps; ids increase like AUTOINCREMENT. */
final class InMemoryIndexStore implements IndexStore {

    private final Map<ArtifactKey, IndexedArtifact> artifacts = new HashMap<>();
    private final Map<Long, List<TypeEntry>> entries = new HashMap<>();
    private long nextId = 1;
    private int replaceCalls;

    @Override
    public Optional<IndexedArtifact> find(ArtifactKey key) {
        return Optional.ofNullable(artifacts.get(key));
    }

    @Override
    public StoredArtifact replace(IndexedArtifact artifact, EntryProducer producer) {
        replaceCalls++;
        List<TypeEntry> produced = new ArrayList<>();
        producer.produce(produced::addAll);
        IndexedArtifact saved = artifact.toBuilder().id(nextId++).build();
        IndexedArtifact previous = artifacts.put(saved.key(), saved);
        if (previous != null) {
            entries.remove(previous.id());
        }
        entries.put(saved.id(), List.copyOf(produced));
        return new StoredArtifact(saved, produced.size(),
                produced.stream().mapToInt(entry -> entry.members().size()).sum());
    }

    @Override
    public List<IndexedType> typesWithoutSources(long artifactId) {
        long[] typeId = {0};
        return entries.getOrDefault(artifactId, List.of()).stream()
                .filter(entry -> entry.type().docSource() != DocSource.SOURCES
                        && entry.type().docSource() != DocSource.JDK)
                .map(entry -> new IndexedType(++typeId[0], artifactId, entry.type()))
                .toList();
    }

    List<TypeEntry> entries(ArtifactKey key) {
        return find(key).map(artifact -> entries.get(artifact.id())).orElse(List.of());
    }

    int replaceCalls() {
        return replaceCalls;
    }
}
