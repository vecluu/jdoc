package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.ArtifactKey;
import io.github.vecluu.jdoc.model.IndexedArtifact;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** JDBC implementation of {@link ArtifactRepository}. */
public final class JdbcArtifactRepository implements ArtifactRepository {

    private static final String SELECT = """
            SELECT id, artifact_key, jar_path, jar_mtime, jar_size, sources_jar, javadoc_jar, indexed_at
            FROM artifacts""";

    private final SqlRunner sql;

    public JdbcArtifactRepository(IndexDatabase database) {
        this.sql = new SqlRunner(Objects.requireNonNull(database, "database"));
    }

    @Override
    public Optional<IndexedArtifact> findByKey(ArtifactKey key) {
        return sql.queryFirst(SELECT + " WHERE artifact_key = ?",
                s -> s.setString(1, key.keyString()), JdbcArtifactRepository::map);
    }

    @Override
    public List<IndexedArtifact> findByJarPath(Path jarPath) {
        return sql.query(SELECT + " WHERE jar_path = ? ORDER BY id",
                s -> s.setString(1, jarPath.toString()), JdbcArtifactRepository::map);
    }

    @Override
    public List<IndexedArtifact> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return sql.query(SELECT + " WHERE id" + IdList.IN_LIST + " ORDER BY id",
                s -> s.setString(1, IdList.json(ids)), JdbcArtifactRepository::map);
    }

    @Override
    public List<IndexedArtifact> findAll() {
        return sql.query(SELECT + " ORDER BY artifact_key", s -> { }, JdbcArtifactRepository::map);
    }

    @Override
    public IndexedArtifact save(IndexedArtifact artifact) {
        Objects.requireNonNull(artifact, "artifact");
        return sql.database().inTransaction(() -> {
            sql.update("DELETE FROM artifacts WHERE artifact_key = ?", s -> s.setString(1, artifact.key().keyString()));
            long id = sql.withStatement("""
                    INSERT INTO artifacts (artifact_key, jar_path, jar_mtime, jar_size, sources_jar, javadoc_jar,
                                           indexed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", s -> {
                s.setString(1, artifact.key().keyString());
                s.setString(2, artifact.jarPath().toString());
                s.setLong(3, artifact.jarMtime());
                s.setLong(4, artifact.jarSize());
                setOptionalPath(s, 5, artifact.sourcesJar());
                setOptionalPath(s, 6, artifact.javadocJar());
                s.setString(7, artifact.indexedAt().toString());
                return Rows.returnedId(s);
            });
            return artifact.toBuilder().id(id).build();
        });
    }

    @Override
    public boolean delete(long id) {
        return sql.update("DELETE FROM artifacts WHERE id = ?", s -> s.setLong(1, id)) > 0;
    }

    private static void setOptionalPath(PreparedStatement statement, int index, Optional<Path> path)
            throws SQLException {
        if (path.isPresent()) {
            statement.setString(index, path.get().toString());
        } else {
            statement.setNull(index, Types.VARCHAR);
        }
    }

    private static IndexedArtifact map(ResultSet row) throws SQLException {
        return IndexedArtifact.builder()
                .id(row.getLong("id"))
                .key(ArtifactKey.parse(row.getString("artifact_key")))
                .jarPath(Path.of(row.getString("jar_path")))
                .jarMtime(row.getLong("jar_mtime"))
                .jarSize(row.getLong("jar_size"))
                .sourcesJar(Optional.ofNullable(row.getString("sources_jar")).map(Path::of))
                .javadocJar(Optional.ofNullable(row.getString("javadoc_jar")).map(Path::of))
                .indexedAt(Instant.parse(row.getString("indexed_at")))
                .build();
    }
}
