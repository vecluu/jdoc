package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.model.ArtifactKey;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** JDBC implementation of {@link FailedArtifactRepository}. */
public final class JdbcFailedArtifactRepository implements FailedArtifactRepository {

    private final SqlRunner sql;

    public JdbcFailedArtifactRepository(IndexDatabase database) {
        this.sql = new SqlRunner(Objects.requireNonNull(database, "database"));
    }

    @Override
    public Optional<FailedArtifact> find(ArtifactKey key) {
        return sql.queryFirst("""
                SELECT artifact_key, jar_path, jar_mtime, jar_size, reason
                FROM failed_artifacts WHERE artifact_key = ?""",
                s -> s.setString(1, key.keyString()), JdbcFailedArtifactRepository::map);
    }

    @Override
    public void save(FailedArtifact failure) {
        Objects.requireNonNull(failure, "failure");
        sql.update("""
                INSERT OR REPLACE INTO failed_artifacts (artifact_key, jar_path, jar_mtime, jar_size, reason)
                VALUES (?, ?, ?, ?, ?)""", s -> {
            s.setString(1, failure.key().keyString());
            s.setString(2, failure.jarPath().toString());
            s.setLong(3, failure.jarMtime());
            s.setLong(4, failure.jarSize());
            s.setString(5, failure.reason());
        });
    }

    @Override
    public boolean delete(ArtifactKey key) {
        return sql.update("DELETE FROM failed_artifacts WHERE artifact_key = ?",
                s -> s.setString(1, key.keyString())) > 0;
    }

    private static FailedArtifact map(ResultSet row) throws SQLException {
        return new FailedArtifact(ArtifactKey.parse(row.getString("artifact_key")), Path.of(row.getString("jar_path")),
                row.getLong("jar_mtime"), row.getLong("jar_size"), row.getString("reason"));
    }
}
