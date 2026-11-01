package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.RenderedDoc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC implementation of {@link DocRepository}; docs are stored as {@link DocCodec} blobs.
 *
 * <p>Batch inserts encode (and deflate) the docs in parallel before writing: the indexer has a single writer thread,
 * and encoding on it would make the parser threads wait.
 */
public final class JdbcDocRepository implements DocRepository {

    /** Below this many docs a batch is encoded on the calling thread. */
    private static final int PARALLEL_THRESHOLD = 256;

    private final SqlRunner sql;

    public JdbcDocRepository(IndexDatabase database) {
        this.sql = new SqlRunner(Objects.requireNonNull(database, "database"));
    }

    /** One member doc to insert, addressed by its member's type and position. */
    private record MemberDocRow(long typeId, int position, RenderedDoc doc) {
    }

    @Override
    public void insertTypeDocs(Map<Long, RenderedDoc> docsByTypeId) {
        List<Long> typeIds = new ArrayList<>();
        List<RenderedDoc> docs = new ArrayList<>();
        docsByTypeId.forEach((typeId, doc) -> {
            if (!isMissing(doc)) {
                typeIds.add(typeId);
                docs.add(doc);
            }
        });
        List<byte[]> encoded = encodeAll(docs);
        sql.database().runInTransaction(() -> sql.withStatement(
                "INSERT INTO type_docs (type_id, doc) VALUES (?, ?)", s -> {
                    for (int i = 0; i < typeIds.size(); i++) {
                        s.setLong(1, typeIds.get(i));
                        s.setBytes(2, encoded.get(i));
                        s.addBatch();
                        if ((i + 1) % JdbcMemberRepository.BATCH_SIZE == 0) {
                            s.executeBatch();
                        }
                    }
                    s.executeBatch();
                    return null;
                }));
    }

    @Override
    public void insertMemberDocs(Map<Long, List<RenderedDoc>> docsByTypeId) {
        List<MemberDocRow> rows = new ArrayList<>();
        docsByTypeId.forEach((typeId, docs) -> {
            for (int position = 0; position < docs.size(); position++) {
                if (!isMissing(docs.get(position))) {
                    rows.add(new MemberDocRow(typeId, position, docs.get(position)));
                }
            }
        });
        List<byte[]> encoded = encodeAll(rows.stream().map(MemberDocRow::doc).toList());
        sql.database().runInTransaction(() -> sql.withStatement("""
                INSERT INTO member_docs (member_id, doc)
                SELECT id, ? FROM members WHERE type_id = ? AND position = ?""", s -> {
            for (int i = 0; i < rows.size(); i++) {
                s.setBytes(1, encoded.get(i));
                s.setLong(2, rows.get(i).typeId());
                s.setInt(3, rows.get(i).position());
                s.addBatch();
                if ((i + 1) % JdbcMemberRepository.BATCH_SIZE == 0) {
                    s.executeBatch();
                }
            }
            s.executeBatch();
            return null;
        }));
    }

    @Override
    public void savePackageDoc(long artifactId, String packageName, RenderedDoc doc) {
        Objects.requireNonNull(packageName, "packageName");
        if (isMissing(doc)) {
            return;
        }
        sql.update("INSERT OR REPLACE INTO package_docs (artifact_id, package, doc) VALUES (?, ?, ?)", s -> {
            s.setLong(1, artifactId);
            s.setString(2, packageName);
            s.setBytes(3, DocCodec.encode(doc));
        });
    }

    @Override
    public Optional<RenderedDoc> typeDoc(long typeId) {
        return sql.queryFirst("SELECT doc FROM type_docs WHERE type_id = ?",
                s -> s.setLong(1, typeId), row -> DocCodec.decode(row.getBytes(1)));
    }

    @Override
    public Optional<RenderedDoc> memberDoc(long memberId) {
        return sql.queryFirst("SELECT doc FROM member_docs WHERE member_id = ?",
                s -> s.setLong(1, memberId), row -> DocCodec.decode(row.getBytes(1)));
    }

    @Override
    public Optional<RenderedDoc> packageDoc(long artifactId, String packageName) {
        return sql.queryFirst("SELECT doc FROM package_docs WHERE artifact_id = ? AND package = ?", s -> {
            s.setLong(1, artifactId);
            s.setString(2, packageName);
        }, row -> DocCodec.decode(row.getBytes(1)));
    }

    private static List<byte[]> encodeAll(List<RenderedDoc> docs) {
        if (docs.size() < PARALLEL_THRESHOLD) {
            return docs.stream().map(DocCodec::encode).toList();
        }
        return docs.parallelStream().map(DocCodec::encode).toList();
    }

    private static boolean isMissing(RenderedDoc doc) {
        return Objects.requireNonNull(doc, "doc").equals(RenderedDoc.missing());
    }
}
