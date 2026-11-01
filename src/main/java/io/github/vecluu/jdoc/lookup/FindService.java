package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.index.MemberRepository;
import io.github.vecluu.jdoc.index.TypeRef;
import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.query.NameMatch;
import io.github.vecluu.jdoc.query.NamePattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * {@code jdoc find <pattern>}: API types and members whose names match a {@link NamePattern}.
 *
 * <p>Types are matched in memory over every type name in scope (exact, prefix, camel humps, substring; a pattern
 * with a {@code .} is matched against the display name {@code java.util.Map.Entry} instead of the simple name).
 * Members are pre-selected in the index by a case-insensitive substring of the pattern and then scored, so camel-hump
 * patterns find members only where the pattern is also a substring. Results are ordered by score (kind, then
 * shorter name), types before members, then alphabetically, and capped at {@link #MAX_HITS}.
 */
@RequiredArgsConstructor
public final class FindService {

    /** Maximum number of hits returned (before pagination). */
    public static final int MAX_HITS = 200;
    /** Maximum number of member rows read from the index for one pattern. */
    static final int MEMBER_SCAN_LIMIT = 5_000;
    private static final int TYPE_BATCH = 256;

    private final ScopedTypes types;
    private final MemberRepository members;

    /** Finds types and members matching the pattern. */
    public FindResult find(String patternText, LookupScope scope) {
        NamePattern pattern = new NamePattern(patternText);
        List<FindHit> hits = new ArrayList<>(typeHits(pattern, scope));
        boolean memberScanFull = false;
        if (!pattern.pattern().contains(".")) {
            List<IndexedMember> rows =
                    members.searchByName(pattern.pattern(), scope.artifactIds(), MEMBER_SCAN_LIMIT);
            memberScanFull = rows.size() >= MEMBER_SCAN_LIMIT;
            hits.addAll(memberHits(pattern, rows, scope));
        }
        hits.sort(Comparator.comparing(FindHit::score)
                .thenComparing(FindHit::isMember)
                .thenComparing(FindHit::reference));
        boolean truncated = hits.size() > MAX_HITS || memberScanFull;
        return new FindResult(pattern.pattern(), hits.size() > MAX_HITS ? hits.subList(0, MAX_HITS) : hits,
                truncated);
    }

    private List<FindHit> typeHits(NamePattern pattern, LookupScope scope) {
        boolean qualified = pattern.pattern().contains(".");
        record Scored(TypeRef ref, NameMatch score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (TypeRef ref : types.repository().listNames(scope.artifactIds())) {
            String name = qualified ? ref.name().displayName() : ref.name().simpleName();
            pattern.match(name).ifPresent(score -> scored.add(new Scored(ref, score)));
        }
        scored.sort(Comparator.comparing(Scored::score)
                .thenComparing(entry -> entry.ref().name().displayName())
                .thenComparing(scope.byRank(entry -> entry.ref().artifactId())));
        List<FindHit> hits = new ArrayList<>();
        Set<TypeName> seen = new HashSet<>();
        for (int from = 0; from < scored.size() && hits.size() <= MAX_HITS; from += TYPE_BATCH) {
            List<Scored> batch = scored.subList(from, Math.min(scored.size(), from + TYPE_BATCH));
            Map<Long, IndexedType> rows = new HashMap<>();
            types.repository().findByIds(batch.stream().map(entry -> entry.ref().typeId()).toList())
                    .forEach(row -> rows.put(row.id(), row));
            for (Scored entry : batch) {
                IndexedType row = rows.get(entry.ref().typeId());
                if (row == null || !seen.add(row.name())) {
                    continue;
                }
                types.locate(List.of(row), scope).stream()
                        .filter(types::isApiVisible)
                        .findFirst()
                        .ifPresent(type -> hits.add(new FindHit(type.name().displayName(), type, Optional.empty(),
                                entry.score())));
            }
        }
        return hits;
    }

    private List<FindHit> memberHits(NamePattern pattern, List<IndexedMember> rows, LookupScope scope) {
        Map<Long, LocatedType> declaring = declaringTypes(rows, scope);
        List<FindHit> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (IndexedMember row : rows) {
            LocatedType type = declaring.get(row.typeId());
            if (type == null || !row.descriptor().visibility().isApi()) {
                continue;
            }
            Optional<NameMatch> score = pattern.match(row.descriptor().name());
            if (score.isPresent() && seen.add(row.descriptor().reference())) {
                hits.add(new FindHit(row.descriptor().reference(), type, Optional.of(row), score.get()));
            }
        }
        return hits;
    }

    /** API-visible declaring types by id; a binary name shadowed by an earlier classpath artifact is dropped. */
    private Map<Long, LocatedType> declaringTypes(List<IndexedMember> rows, LookupScope scope) {
        List<Long> typeIds = rows.stream().map(IndexedMember::typeId).distinct().toList();
        List<LocatedType> located = types.locate(types.repository().findByIds(typeIds), scope);
        Map<Long, LocatedType> result = new HashMap<>();
        for (LocatedType type : ScopedTypes.firstPerName(located)) {
            if (types.isApiVisible(type)) {
                result.put(type.id(), type);
            }
        }
        return result;
    }
}
