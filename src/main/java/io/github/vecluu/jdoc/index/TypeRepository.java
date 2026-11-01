package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The {@code types} table (and, for writes, the {@code supertypes} rows of each type).
 *
 * <p>Read methods take the artifact-id scope; results are ordered by binary name, then artifact id. Returned
 * {@link IndexedType}s carry their full descriptor, including direct supertypes.
 */
public interface TypeRepository {

    /**
     * Inserts the types of one artifact, including each descriptor's direct supertypes, in one transaction.
     *
     * @return the stored types with their ids, in input order
     */
    List<IndexedType> insertAll(long artifactId, List<TypeDescriptor> types);

    /** The type with this id. */
    Optional<IndexedType> findById(long id);

    /** The types with these ids (unscoped), ordered by binary name, then artifact id. */
    List<IndexedType> findByIds(Collection<Long> ids);

    /** Types with this binary name ({@code java.util.Map$Entry}); several if more than one jar has it. */
    List<IndexedType> findByBinaryName(TypeName name, Collection<Long> artifactIds);

    /** Types whose simple name equals {@code simpleName}, ignoring case. */
    List<IndexedType> findBySimpleName(String simpleName, Collection<Long> artifactIds);

    /** Types with this display name ({@code java.util.Map.Entry}), case-sensitive. */
    List<IndexedType> findByDisplayName(String displayName, Collection<Long> artifactIds);

    /** All types of one package (top-level and nested). */
    List<IndexedType> findByPackage(String packageName, Collection<Long> artifactIds);

    /**
     * The types nested in {@code outer} at any depth (binary names starting with {@code outer$}); one range scan of
     * the binary-name index instead of reading the whole package.
     */
    List<IndexedType> findNested(TypeName outer, Collection<Long> artifactIds);

    /** Types whose display name contains {@code text}, ignoring (ASCII) case; at most {@code limit} rows. */
    List<IndexedType> searchByName(String text, Collection<Long> artifactIds, int limit);

    /** Every type name in scope, for name matching done in Java (e.g. camel humps). */
    List<TypeRef> listNames(Collection<Long> artifactIds);

    /**
     * The type names in scope whose {@code column} contains the characters of {@code subsequence} in order, with
     * anything in between, ignoring ASCII case like {@code LIKE} (other characters compare exactly); an empty
     * {@code subsequence} lists every name. Used by {@code jdoc find} to pre-select the names it matches in memory:
     * every prefix, substring and camel-hump match of a pattern contains the pattern's ASCII characters this way.
     */
    List<TypeRef> listNames(Collection<Long> artifactIds, NameColumn column, String subsequence);

    /** The name column {@link #listNames(Collection, NameColumn, String)} filters on. */
    enum NameColumn {
        /** {@code Entry} of {@code java.util.Map$Entry}. */
        SIMPLE_NAME("t.simple_name"),
        /** {@code java.util.Map.Entry}. */
        DISPLAY_NAME("t.display_name");

        private final String sql;

        NameColumn(String sql) {
            this.sql = sql;
        }

        String sql() {
            return sql;
        }
    }

    /** Distinct package names in scope, sorted. */
    List<String> listPackages(Collection<Long> artifactIds);

    /**
     * Types of one artifact whose docs do not come from source text (doc source other than SOURCES and JDK), for the
     * missing-sources report.
     */
    List<IndexedType> findWithoutSources(long artifactId);

    /** Number of types stored for an artifact. */
    int countByArtifact(long artifactId);
}
