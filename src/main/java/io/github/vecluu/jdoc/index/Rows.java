package io.github.vecluu.jdoc.index;

import io.github.vecluu.jdoc.error.IndexException;
import io.github.vecluu.jdoc.model.SourceRange;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;

/** Shared row-mapping helpers. */
final class Rows {

    private Rows() {
    }

    /** Runs an {@code INSERT ... RETURNING id} statement and returns the id. */
    static long returnedId(PreparedStatement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new IndexException("insert returned no id");
            }
            return rows.getLong(1);
        }
    }

    static void setRange(PreparedStatement statement, int startIndex, Optional<SourceRange> range)
            throws SQLException {
        if (range.isPresent()) {
            statement.setInt(startIndex, range.get().start());
            statement.setInt(startIndex + 1, range.get().end());
        } else {
            statement.setNull(startIndex, Types.INTEGER);
            statement.setNull(startIndex + 1, Types.INTEGER);
        }
    }

    static Optional<SourceRange> range(ResultSet row, String startColumn, String endColumn) throws SQLException {
        int start = row.getInt(startColumn);
        if (row.wasNull()) {
            return Optional.empty();
        }
        return Optional.of(new SourceRange(start, row.getInt(endColumn)));
    }

    static int flag(boolean value) {
        return value ? 1 : 0;
    }
}
