package io.github.vecluu.jdoc.lookup;

import io.github.vecluu.jdoc.index.MemberRepository;
import io.github.vecluu.jdoc.model.IndexedMember;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * {@code jdoc member <name>}: every API member with that name, declared by an API type in scope, as
 * {@code Type#name(params)} ({@link MemberMatch#reference()}).
 *
 * <p>Names match ignoring case; if any member matches with exact case, only exact-case matches are returned.
 * Results are ordered by declaring type name, then declaration order. A type shadowed by an earlier classpath
 * artifact with the same binary name is skipped.
 */
@RequiredArgsConstructor
public final class MemberSearchService {

    private final ScopedTypes types;
    private final MemberRepository members;

    /** Members named {@code name}; empty if there are none. */
    public List<MemberMatch> search(String name, LookupScope scope) {
        String wanted = name == null ? "" : name.strip();
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<IndexedMember> rows = members.findByName(wanted, scope.artifactIds()).stream()
                .filter(row -> row.descriptor().visibility().isApi())
                .toList();
        List<IndexedMember> exactCase = rows.stream().filter(row -> row.descriptor().name().equals(wanted)).toList();
        if (!exactCase.isEmpty()) {
            rows = exactCase;
        }
        List<Long> typeIds = rows.stream().map(IndexedMember::typeId).distinct().toList();
        Map<Long, LocatedType> declaring = new HashMap<>();
        for (LocatedType type : ScopedTypes.firstPerName(types.locate(types.repository().findByIds(typeIds), scope))) {
            if (types.isApiVisible(type)) {
                declaring.put(type.id(), type);
            }
        }
        List<MemberMatch> result = new ArrayList<>();
        for (IndexedMember row : rows) {
            LocatedType type = declaring.get(row.typeId());
            if (type != null) {
                result.add(new MemberMatch(row, type, false));
            }
        }
        return List.copyOf(result);
    }
}
