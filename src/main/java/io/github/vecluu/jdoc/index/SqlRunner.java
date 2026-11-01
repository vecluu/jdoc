package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.error.IndexException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/** Small JDBC helper for the repositories: runs statements and wraps {@link SQLException} into {@link IndexException}. */
@RequiredArgsConstructor
final class SqlRunner {

    /** Binds parameters to a prepared statement. */
    @FunctionalInterface
    interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /** Maps the current row of a result set. */
    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    /** Works with a prepared statement, e.g. to run a batch. */
    @FunctionalInterface
    interface StatementWork<T> {
        T run(PreparedStatement statement) throws SQLException;
    }

    private final IndexDatabase database;

    IndexDatabase database() {
        return database;
    }

    <T> List<T> query(String sql, Binder binder, RowMapper<T> mapper) {
        return withStatement(sql, statement -> {
            binder.bind(statement);
            List<T> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(mapper.map(rows));
                }
            }
            return result;
        });
    }

    <T> Optional<T> queryFirst(String sql, Binder binder, RowMapper<T> mapper) {
        List<T> rows = query(sql, binder, mapper);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    int update(String sql, Binder binder) {
        return withStatement(sql, statement -> {
            binder.bind(statement);
            return statement.executeUpdate();
        });
    }

    <T> T withStatement(String sql, StatementWork<T> work) {
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            return work.run(statement);
        } catch (SQLException e) {
            throw new IndexException("index query failed (" + e.getMessage() + "): " + oneLine(sql), e);
        }
    }

    private static String oneLine(String sql) {
        return sql.replaceAll("\\s+", " ").strip();
    }
}
