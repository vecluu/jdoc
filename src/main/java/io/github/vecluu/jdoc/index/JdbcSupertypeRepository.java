package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.IndexedType;
import io.github.vecluu.jdoc.model.TypeName;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** JDBC implementation of {@link SupertypeRepository}. */
public final class JdbcSupertypeRepository implements SupertypeRepository {

    private final SqlRunner sql;
    private final JdbcTypeRepository types;

    public JdbcSupertypeRepository(IndexDatabase database) {
        this.sql = new SqlRunner(Objects.requireNonNull(database, "database"));
        this.types = new JdbcTypeRepository(database);
    }

    @Override
    public List<TypeName> supertypesOf(long typeId) {
        return sql.query("SELECT supertype FROM supertypes WHERE type_id = ? ORDER BY position",
                s -> s.setLong(1, typeId), row -> TypeName.of(row.getString(1)));
    }

    @Override
    public List<IndexedType> directSubtypesOf(TypeName supertype, Collection<Long> artifactIds) {
        Objects.requireNonNull(supertype, "supertype");
        if (artifactIds.isEmpty()) {
            return List.of();
        }
        List<IndexedType> rows = sql.query("SELECT " + JdbcTypeRepository.COLUMNS + """
                 FROM supertypes st JOIN types t ON t.id = st.type_id
                WHERE st.supertype = ? AND t.artifact_id IN (SELECT value FROM json_each(?))
                ORDER BY t.binary_name, t.artifact_id""", s -> {
            s.setString(1, supertype.binaryName());
            s.setString(2, IdList.json(artifactIds));
        }, JdbcTypeRepository::map);
        return types.withSupertypes(rows);
    }
}
