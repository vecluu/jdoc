package io.github.vecluu.jdoc.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vecluu.jdoc.config.CachePaths;
import io.github.vecluu.jdoc.error.IndexException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IndexDatabaseTest {

    @TempDir
    Path tmp;

    @Test
    void createsCacheDirectoryAndFileWithCurrentSchema() {
        CachePaths paths = new CachePaths(tmp.resolve("cache"));
        try (IndexDatabase database = IndexDatabase.open(paths)) {
            assertThat(database.file()).isEqualTo(paths.indexDb());
            assertThat(database.schemaVersion()).isEqualTo(Schema.VERSION);
        }
        assertThat(paths.indexDb()).exists();
    }

    @Test
    void usesWalModeAndForeignKeys() throws SQLException {
        try (IndexDatabase database = open()) {
            assertThat(pragma(database, "journal_mode")).isEqualTo("wal");
            assertThat(pragma(database, "foreign_keys")).isEqualTo("1");
            assertThat(Long.parseLong(pragma(database, "busy_timeout"))).isPositive();
        }
    }

    @Test
    void keepsDataWhenReopenedWithSameVersion() {
        try (IndexDatabase database = open()) {
            new JdbcArtifactRepository(database).save(IndexFixtures.artifact("g:a:1"));
        }
        try (IndexDatabase database = open()) {
            assertThat(new JdbcArtifactRepository(database).findAll()).hasSize(1);
        }
    }

    @Test
    void rebuildsSchemaOnVersionMismatch() throws SQLException {
        try (IndexDatabase database = open()) {
            new JdbcArtifactRepository(database).save(IndexFixtures.artifact("g:a:1"));
            try (Statement statement = database.connection().createStatement()) {
                statement.execute("CREATE TABLE obsolete (x INTEGER)");
                statement.execute("PRAGMA user_version = 999");
            }
        }
        try (IndexDatabase database = open()) {
            assertThat(database.schemaVersion()).isEqualTo(Schema.VERSION);
            assertThat(new JdbcArtifactRepository(database).findAll()).isEmpty();
            assertThat(pragmaQuery(database, "SELECT count(*) FROM sqlite_master WHERE name = 'obsolete'"))
                    .isEqualTo("0");
        }
    }

    @Test
    void commitsTransaction() {
        try (IndexDatabase database = open()) {
            JdbcArtifactRepository artifacts = new JdbcArtifactRepository(database);
            database.runInTransaction(() -> artifacts.save(IndexFixtures.artifact("g:a:1")));
            assertThat(database.inTransaction()).isFalse();
            assertThat(artifacts.findAll()).hasSize(1);
        }
    }

    @Test
    void rollsBackTransactionOnFailure() {
        try (IndexDatabase database = open()) {
            JdbcArtifactRepository artifacts = new JdbcArtifactRepository(database);
            assertThatThrownBy(() -> database.runInTransaction(() -> {
                artifacts.save(IndexFixtures.artifact("g:a:1"));
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class).hasMessage("boom");
            assertThat(database.inTransaction()).isFalse();
            assertThat(artifacts.findAll()).isEmpty();
            artifacts.save(IndexFixtures.artifact("g:a:2"));
            assertThat(artifacts.findAll()).hasSize(1);
        }
    }

    @Test
    void nestedTransactionJoinsOuterAndRollsBackTogether() {
        try (IndexDatabase database = open()) {
            JdbcArtifactRepository artifacts = new JdbcArtifactRepository(database);
            assertThatThrownBy(() -> database.runInTransaction(() -> {
                database.runInTransaction(() -> artifacts.save(IndexFixtures.artifact("g:a:1")));
                assertThat(database.inTransaction()).isTrue();
                throw new IllegalStateException("outer fails");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(artifacts.findAll()).isEmpty();
        }
    }

    @Test
    void returnsTransactionResult() {
        try (IndexDatabase database = open()) {
            assertThat(database.inTransaction(() -> 42)).isEqualTo(42);
        }
    }

    @Test
    void secondConnectionCanWriteAfterFirstCommits() {
        try (IndexDatabase first = open(); IndexDatabase second = IndexDatabase.open(dbFile(), Duration.ofMillis(200))) {
            first.runInTransaction(() -> new JdbcArtifactRepository(first).save(IndexFixtures.artifact("g:a:1")));
            new JdbcArtifactRepository(second).save(IndexFixtures.artifact("g:a:2"));
            assertThat(new JdbcArtifactRepository(first).findAll()).hasSize(2);
        }
    }

    @Test
    void opensInBackgroundAndIsUsableRightAway() {
        CachePaths paths = new CachePaths(tmp.resolve("cache"));
        try (IndexDatabase database = IndexDatabase.openInBackground(paths)) {
            assertThat(database.file()).isEqualTo(paths.indexDb());
            assertThat(database.schemaVersion()).isEqualTo(Schema.VERSION);
            assertThat(database.inTransaction(() -> 42)).isEqualTo(42);
        }
        assertThat(paths.indexDb()).exists();
    }

    @Test
    void backgroundOpenFailureSurfacesOnFirstUseAndCloseIgnoresIt() throws Exception {
        CachePaths paths = new CachePaths(tmp.resolve("cache"));
        Files.createDirectories(paths.indexDb());
        IndexDatabase database = IndexDatabase.openInBackground(paths);

        assertThatThrownBy(database::schemaVersion).isInstanceOf(IndexException.class);
        assertThatThrownBy(database::schemaVersion).isInstanceOf(IndexException.class);
        database.close();
    }

    @Test
    void closingAnUnusedBackgroundOpenClosesItsConnection() {
        CachePaths paths = new CachePaths(tmp.resolve("cache"));
        IndexDatabase.openInBackground(paths).close();

        try (IndexDatabase again = IndexDatabase.open(paths)) {
            assertThat(again.schemaVersion()).isEqualTo(Schema.VERSION);
        }
    }

    @Test
    void failsCleanlyOnUnopenableFile() throws Exception {
        Path directory = Files.createDirectory(tmp.resolve("is-a-directory"));
        assertThatThrownBy(() -> IndexDatabase.open(directory, Duration.ofMillis(100)))
                .isInstanceOf(IndexException.class);
    }

    private Path dbFile() {
        return tmp.resolve("index.db");
    }

    private IndexDatabase open() {
        return IndexDatabase.open(dbFile(), Duration.ofSeconds(5));
    }

    private static String pragma(IndexDatabase database, String name) throws SQLException {
        return pragmaQuery(database, "PRAGMA " + name);
    }

    private static String pragmaQuery(IndexDatabase database, String sql) throws SQLException {
        try (Statement statement = database.connection().createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }
}
