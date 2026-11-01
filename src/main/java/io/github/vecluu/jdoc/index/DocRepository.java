package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rendered docs of types, members and packages ({@code type_docs}, {@code member_docs}, {@code package_docs}),
 * written at index time so lookups never parse sources or javadoc pages.
 *
 * <p>Absence means "no doc": {@link RenderedDoc#missing()} is never stored, and reads return empty for it. Rows are
 * deleted with their type, member or artifact.
 */
public interface DocRepository {

    /** Stores the docs of types (by type id), skipping {@link RenderedDoc#missing()}. */
    void insertTypeDocs(Map<Long, RenderedDoc> docsByTypeId);

    /**
     * Stores member docs by position: for each type id, the list is aligned with the member list passed to
     * {@link MemberRepository#insertAll(Map)} for that type (element {@code i} documents the member at position
     * {@code i}). {@link RenderedDoc#missing()} entries are skipped; the members must already exist.
     */
    void insertMemberDocs(Map<Long, List<RenderedDoc>> docsByTypeId);

    /** Stores (or replaces) the doc of a package of an artifact; {@link RenderedDoc#missing()} is skipped. */
    void savePackageDoc(long artifactId, String packageName, RenderedDoc doc);

    /** The stored doc of a type. */
    Optional<RenderedDoc> typeDoc(long typeId);

    /** The stored doc of a member. */
    Optional<RenderedDoc> memberDoc(long memberId);

    /** The stored doc of a package of an artifact (default package: {@code ""}). */
    Optional<RenderedDoc> packageDoc(long artifactId, String packageName);
}
