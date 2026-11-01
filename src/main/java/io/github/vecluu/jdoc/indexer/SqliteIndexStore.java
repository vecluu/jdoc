package io.github.vecluu.jdoc.indexer;

import io.github.vecluu.jdoc.index.ArtifactRepository;
import io.github.vecluu.jdoc.index.IndexDatabase;
import io.github.vecluu.jdoc.index.MemberRepository;
import io.github.vecluu.jdoc.index.TypeRepository;
import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * {@link IndexStore} on T03's repositories. Like the {@link IndexDatabase} it wraps, an instance must be confined to
 * one thread at a time.
 */
@RequiredArgsConstructor
public final class SqliteIndexStore implements IndexStore {

    private final IndexDatabase database;
    private final ArtifactRepository artifacts;
    private final TypeRepository types;
    private final MemberRepository members;

    @Override
    public Optional<IndexedArtifact> find(ArtifactKey key) {
        return artifacts.findByKey(key);
    }

    @Override
    public StoredArtifact replace(IndexedArtifact artifact, EntryProducer entries) {
        return database.inTransaction(() -> {
            IndexedArtifact saved = artifacts.save(artifact);
            int[] counts = new int[2];
            entries.produce(batch -> {
                if (batch.isEmpty()) {
                    return;
                }
                List<IndexedType> inserted = types.insertAll(saved.id(),
                        batch.stream().map(TypeEntry::type).toList());
                Map<Long, List<MemberDescriptor>> byType = new LinkedHashMap<>();
                for (int i = 0; i < inserted.size(); i++) {
                    byType.put(inserted.get(i).id(), batch.get(i).members());
                }
                counts[0] += inserted.size();
                counts[1] += members.insertAll(byType);
            });
            return new StoredArtifact(saved, counts[0], counts[1]);
        });
    }

    @Override
    public List<IndexedType> typesWithoutSources(long artifactId) {
        return types.findWithoutSources(artifactId);
    }
}
