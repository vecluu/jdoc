package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.IndexedMember;
import io.github.vecluu.jdoc.model.MemberDescriptor;
import io.github.vecluu.jdoc.model.MemberKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** JDBC implementation of {@link MemberRepository}. */
public final class JdbcMemberRepository implements MemberRepository {

    /** Rows per JDBC batch. */
    static final int BATCH_SIZE = 1000;

    private static final String SELECT = """
            SELECT m.id, m.type_id, t.binary_name, m.name, m.kind, m.signature, m.parameters, m.visibility,
                   m.is_static, m.deprecated, m.first_sentence, m.range_start, m.range_end, m.doc_start, m.doc_end
            FROM members m JOIN types t ON t.id = m.type_id""";
    private static final String SCOPED = " t.artifact_id" + IdList.IN_LIST;
    private static final String ORDER = " ORDER BY t.binary_name, t.artifact_id, m.position";

    private final SqlRunner sql;

    public JdbcMemberRepository(IndexDatabase database) {
        this.sql = new SqlRunner(Objects.requireNonNull(database, "database"));
    }

    @Override
    public int insertAll(Map<Long, List<MemberDescriptor>> membersByTypeId) {
        Map<Long, List<MemberDescriptor>> copy = new LinkedHashMap<>(membersByTypeId);
        return sql.database().inTransaction(() -> sql.withStatement("""
                INSERT INTO members (type_id, position, name, kind, signature, parameters, visibility, is_static,
                                     deprecated, first_sentence, range_start, range_end, doc_start, doc_end)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", s -> {
            int total = 0;
            int pending = 0;
            for (Map.Entry<Long, List<MemberDescriptor>> entry : copy.entrySet()) {
                List<MemberDescriptor> members = entry.getValue();
                for (int position = 0; position < members.size(); position++) {
                    MemberDescriptor member = members.get(position);
                    s.setLong(1, entry.getKey());
                    s.setInt(2, position);
                    s.setString(3, member.name());
                    s.setString(4, member.kind().name());
                    s.setString(5, member.signature());
                    s.setString(6, ParameterCodec.encode(member.parameters()));
                    s.setString(7, member.visibility().name());
                    s.setInt(8, Rows.flag(member.isStatic()));
                    s.setInt(9, Rows.flag(member.deprecated()));
                    s.setString(10, member.firstSentence());
                    Rows.setRange(s, 11, member.range());
                    Rows.setRange(s, 13, member.docRange());
                    s.addBatch();
                    total++;
                    if (++pending == BATCH_SIZE) {
                        s.executeBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) {
                s.executeBatch();
            }
            return total;
        }));
    }

    @Override
    public List<IndexedMember> membersOf(long typeId) {
        return sql.query(SELECT + " WHERE m.type_id = ? ORDER BY m.position",
                s -> s.setLong(1, typeId), JdbcMemberRepository::map);
    }

    @Override
    public Map<Long, List<IndexedMember>> membersOf(Collection<Long> typeIds) {
        if (typeIds.isEmpty()) {
            return Map.of();
        }
        List<IndexedMember> rows = sql.query(
                SELECT + " WHERE m.type_id" + IdList.IN_LIST + " ORDER BY m.type_id, m.position",
                s -> s.setString(1, IdList.json(typeIds)), JdbcMemberRepository::map);
        Map<Long, List<IndexedMember>> grouped = new LinkedHashMap<>();
        for (IndexedMember member : rows) {
            grouped.computeIfAbsent(member.typeId(), id -> new ArrayList<>()).add(member);
        }
        grouped.replaceAll((id, members) -> List.copyOf(members));
        return Collections.unmodifiableMap(grouped);
    }

    @Override
    public List<IndexedMember> findByName(String name, Collection<Long> artifactIds) {
        Objects.requireNonNull(name, "name");
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        return sql.query(SELECT + " WHERE m.name = ? COLLATE NOCASE AND" + SCOPED + ORDER, s -> {
            s.setString(1, name);
            s.setString(2, IdList.json(artifactIds));
        }, JdbcMemberRepository::map);
    }

    @Override
    public List<IndexedMember> searchByName(String text, Collection<Long> artifactIds, int limit) {
        if (artifactIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        return sql.query(SELECT + " WHERE m.name LIKE ? ESCAPE '\\' AND" + SCOPED + ORDER + " LIMIT ?", s -> {
            s.setString(1, IdList.containsPattern(text));
            s.setString(2, IdList.json(artifactIds));
            s.setInt(3, limit);
        }, JdbcMemberRepository::map);
    }

    private static IndexedMember map(ResultSet row) throws SQLException {
        MemberDescriptor descriptor = MemberDescriptor.builder()
                .declaringType(TypeName.of(row.getString("binary_name")))
                .name(row.getString("name"))
                .kind(MemberKind.valueOf(row.getString("kind")))
                .signature(row.getString("signature"))
                .parameters(ParameterCodec.decode(row.getString("parameters")))
                .visibility(Visibility.valueOf(row.getString("visibility")))
                .isStatic(row.getInt("is_static") != 0)
                .deprecated(row.getInt("deprecated") != 0)
                .firstSentence(row.getString("first_sentence"))
                .range(Rows.range(row, "range_start", "range_end"))
                .docRange(Rows.range(row, "doc_start", "doc_end"))
                .build();
        return new IndexedMember(row.getLong("id"), row.getLong("type_id"), descriptor);
    }
}
