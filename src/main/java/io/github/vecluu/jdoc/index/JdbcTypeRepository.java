package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.DocSource;
import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeDescriptor;
import io.github.vecluu.jdoc.model.TypeKind;
import io.github.vecluu.jdoc.model.TypeName;
import io.github.vecluu.jdoc.model.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** JDBC implementation of {@link TypeRepository}. */
public final class JdbcTypeRepository implements TypeRepository {

    static final String COLUMNS = """
            t.id, t.artifact_id, t.binary_name, t.kind, t.visibility, t.modifiers, t.type_params, t.deprecated,
            t.first_sentence, t.doc_source, t.entry_path, t.decl_start, t.decl_end""";

    private static final String SELECT = "SELECT " + COLUMNS + " FROM types t";
    private static final String ORDER = " ORDER BY t.binary_name, t.artifact_id";
    private static final String SCOPED = " t.artifact_id" + IdList.IN_LIST;

    private final SqlRunner sql;

    public JdbcTypeRepository(IndexDatabase database) {
        this.sql = new SqlRunner(Objects.requireNonNull(database, "database"));
    }

    @Override
    public List<IndexedType> insertAll(long artifactId, List<TypeDescriptor> types) {
        List<TypeDescriptor> copy = List.copyOf(types);
        return sql.database().inTransaction(() -> {
            List<IndexedType> stored = insertTypes(artifactId, copy);
            insertSupertypes(stored);
            return stored;
        });
    }

    private List<IndexedType> insertTypes(long artifactId, List<TypeDescriptor> types) {
        return sql.withStatement("""
                INSERT INTO types (artifact_id, binary_name, simple_name, display_name, package, kind, visibility,
                                   modifiers, type_params, deprecated, first_sentence, doc_source, entry_path,
                                   decl_start, decl_end)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", s -> {
            List<IndexedType> stored = new ArrayList<>(types.size());
            for (TypeDescriptor type : types) {
                TypeName name = type.name();
                s.setLong(1, artifactId);
                s.setString(2, name.binaryName());
                s.setString(3, name.simpleName());
                s.setString(4, name.displayName());
                s.setString(5, name.packageName());
                s.setString(6, type.kind().name());
                s.setString(7, type.visibility().name());
                s.setString(8, type.modifiers());
                s.setString(9, type.typeParameters());
                s.setInt(10, Rows.flag(type.deprecated()));
                s.setString(11, type.firstSentence());
                s.setString(12, type.docSource().name());
                s.setString(13, type.entryPath());
                Rows.setRange(s, 14, type.declarationRange());
                stored.add(new IndexedType(Rows.returnedId(s), artifactId, type));
            }
            return stored;
        });
    }

    private void insertSupertypes(List<IndexedType> types) {
        sql.withStatement("INSERT INTO supertypes (type_id, position, supertype) VALUES (?, ?, ?)", s -> {
            int pending = 0;
            for (IndexedType type : types) {
                List<TypeName> supertypes = type.descriptor().supertypes();
                for (int i = 0; i < supertypes.size(); i++) {
                    s.setLong(1, type.id());
                    s.setInt(2, i);
                    s.setString(3, supertypes.get(i).binaryName());
                    s.addBatch();
                    if (++pending == JdbcMemberRepository.BATCH_SIZE) {
                        s.executeBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) {
                s.executeBatch();
            }
            return null;
        });
    }

    @Override
    public Optional<IndexedType> findById(long id) {
        List<IndexedType> found = findByIds(List.of(id));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }

    @Override
    public List<IndexedType> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return select(" WHERE t.id" + IdList.IN_LIST, s -> s.setString(1, IdList.json(ids)));
    }

    @Override
    public List<IndexedType> findByBinaryName(TypeName name, Collection<Long> artifactIds) {
        return scoped(artifactIds, "t.binary_name = ?", name.binaryName());
    }

    @Override
    public List<IndexedType> findBySimpleName(String simpleName, Collection<Long> artifactIds) {
        return scoped(artifactIds, "t.simple_name = ? COLLATE NOCASE", simpleName);
    }

    @Override
    public List<IndexedType> findByDisplayName(String displayName, Collection<Long> artifactIds) {
        return scoped(artifactIds, "t.display_name = ?", displayName);
    }

    @Override
    public List<IndexedType> findByPackage(String packageName, Collection<Long> artifactIds) {
        return scoped(artifactIds, "t.package = ?", packageName);
    }

    @Override
    public List<IndexedType> searchByName(String text, Collection<Long> artifactIds, int limit) {
        if (artifactIds.isEmpty() || limit <= 0) {
            return List.of();
        }
        return selectTail(" WHERE t.display_name LIKE ? ESCAPE '\\' AND" + SCOPED + ORDER + " LIMIT ?", s -> {
            s.setString(1, IdList.containsPattern(text));
            s.setString(2, IdList.json(artifactIds));
            s.setInt(3, limit);
        });
    }

    @Override
    public List<TypeRef> listNames(Collection<Long> artifactIds) {
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        return sql.query("SELECT t.id, t.artifact_id, t.binary_name FROM types t WHERE" + SCOPED + ORDER,
                s -> s.setString(1, IdList.json(artifactIds)),
                row -> new TypeRef(row.getLong(1), row.getLong(2), TypeName.of(row.getString(3))));
    }

    @Override
    public List<String> listPackages(Collection<Long> artifactIds) {
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        return sql.query("SELECT DISTINCT t.package FROM types t WHERE" + SCOPED + " ORDER BY t.package",
                s -> s.setString(1, IdList.json(artifactIds)), row -> row.getString(1));
    }

    @Override
    public List<IndexedType> findWithoutSources(long artifactId) {
        return select(" WHERE t.artifact_id = ? AND t.doc_source NOT IN (?, ?)", s -> {
            s.setLong(1, artifactId);
            s.setString(2, DocSource.SOURCES.name());
            s.setString(3, DocSource.JDK.name());
        });
    }

    @Override
    public int countByArtifact(long artifactId) {
        return sql.queryFirst("SELECT count(*) FROM types WHERE artifact_id = ?", s -> s.setLong(1, artifactId),
                row -> row.getInt(1)).orElse(0);
    }

    private List<IndexedType> scoped(Collection<Long> artifactIds, String condition, String value) {
        Objects.requireNonNull(value, "value");
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        return select(" WHERE " + condition + " AND" + SCOPED, s -> {
            s.setString(1, value);
            s.setString(2, IdList.json(artifactIds));
        });
    }

    private List<IndexedType> select(String where, SqlRunner.Binder binder) {
        return selectTail(where + ORDER, binder);
    }

    private List<IndexedType> selectTail(String tail, SqlRunner.Binder binder) {
        return withSupertypes(sql.query(SELECT + tail, binder, JdbcTypeRepository::map));
    }

    /** Fills in the direct supertypes of freshly mapped rows with one extra query. */
    List<IndexedType> withSupertypes(List<IndexedType> types) {
        if (types.isEmpty()) {
            return types;
        }
        Map<Long, List<TypeName>> supertypes = new HashMap<>();
        sql.query("""
                SELECT type_id, supertype FROM supertypes
                WHERE type_id IN (SELECT value FROM json_each(?)) ORDER BY type_id, position""",
                s -> s.setString(1, IdList.json(types.stream().map(IndexedType::id).toList())),
                row -> supertypes.computeIfAbsent(row.getLong(1), id -> new ArrayList<>())
                        .add(TypeName.of(row.getString(2))));
        return types.stream()
                .map(type -> {
                    List<TypeName> direct = supertypes.get(type.id());
                    return direct == null ? type : new IndexedType(type.id(), type.artifactId(),
                            type.descriptor().toBuilder().supertypes(direct).build());
                })
                .toList();
    }

    /** Maps the {@link #COLUMNS} of a row, without supertypes. */
    static IndexedType map(ResultSet row) throws SQLException {
        TypeDescriptor descriptor = TypeDescriptor.builder()
                .name(TypeName.of(row.getString("binary_name")))
                .kind(TypeKind.valueOf(row.getString("kind")))
                .visibility(Visibility.valueOf(row.getString("visibility")))
                .modifiers(row.getString("modifiers"))
                .typeParameters(row.getString("type_params"))
                .deprecated(row.getInt("deprecated") != 0)
                .firstSentence(row.getString("first_sentence"))
                .docSource(DocSource.valueOf(row.getString("doc_source")))
                .entryPath(row.getString("entry_path"))
                .declarationRange(Rows.range(row, "decl_start", "decl_end"))
                .build();
        return new IndexedType(row.getLong("id"), row.getLong("artifact_id"), descriptor);
    }
}
