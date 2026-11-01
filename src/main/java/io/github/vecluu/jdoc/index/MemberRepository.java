package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** The {@code members} table. Members keep the order in which they were inserted for their type. */
public interface MemberRepository {

    /**
     * Batch-inserts members for several types in one transaction; each list keeps its order.
     *
     * @return number of rows inserted
     */
    int insertAll(Map<Long, List<MemberDescriptor>> membersByTypeId);

    /** {@link #insertAll(Map)} for one type. */
    default int insertAll(long typeId, List<MemberDescriptor> members) {
        return insertAll(Map.of(typeId, members));
    }

    /** Members of a type, in declaration (insertion) order. */
    List<IndexedMember> membersOf(long typeId);

    /** Members of several types (unscoped), grouped by type id, each in declaration order. */
    Map<Long, List<IndexedMember>> membersOf(Collection<Long> typeIds);

    /** Members named {@code name} (ignoring case) of types in scope, ordered by type name then declaration. */
    List<IndexedMember> findByName(String name, Collection<Long> artifactIds);

    /** Members whose name contains {@code text}, ignoring (ASCII) case; at most {@code limit} rows. */
    List<IndexedMember> searchByName(String text, Collection<Long> artifactIds, int limit);
}
