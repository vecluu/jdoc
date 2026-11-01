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

    /**
     * The distinct names (case-sensitive) of members of types in scope that contain the characters of
     * {@code subsequence} in order, with anything in between, ignoring ASCII case like {@code LIKE} (other characters
     * compare exactly); an empty {@code subsequence} lists every name. No particular order. Used by
     * {@code jdoc find} to match camel-hump patterns in memory: every hump match of a pattern contains the pattern
     * as such a subsequence.
     */
    List<String> listNames(Collection<Long> artifactIds, String subsequence);

    /**
     * Members whose name equals one of {@code names} (case-sensitive) of types in scope, ordered by type name then
     * declaration; at most {@code limit} rows.
     */
    List<IndexedMember> findByNames(Collection<String> names, Collection<Long> artifactIds, int limit);
}
