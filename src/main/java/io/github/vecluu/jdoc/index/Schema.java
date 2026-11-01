package io.github.vecluu.jdoc.index;

import java.util.List;

/** DDL of the index database. Bump {@link #VERSION} on any change: older files are dropped and rebuilt. */
final class Schema {

    /** Value stored in {@code PRAGMA user_version}. */
    static final int VERSION = 1;

    static final List<String> STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS artifacts (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                artifact_key TEXT    NOT NULL UNIQUE,
                jar_path     TEXT    NOT NULL,
                jar_mtime    INTEGER NOT NULL,
                jar_size     INTEGER NOT NULL,
                sources_jar  TEXT,
                javadoc_jar  TEXT,
                indexed_at   TEXT    NOT NULL
            )""",
            "CREATE INDEX IF NOT EXISTS artifacts_jar_path ON artifacts (jar_path)",
            """
            CREATE TABLE IF NOT EXISTS types (
                id             INTEGER PRIMARY KEY,
                artifact_id    INTEGER NOT NULL REFERENCES artifacts (id) ON DELETE CASCADE,
                binary_name    TEXT    NOT NULL,
                simple_name    TEXT    NOT NULL,
                display_name   TEXT    NOT NULL,
                package        TEXT    NOT NULL,
                kind           TEXT    NOT NULL,
                visibility     TEXT    NOT NULL,
                modifiers      TEXT    NOT NULL,
                type_params    TEXT    NOT NULL,
                deprecated     INTEGER NOT NULL,
                first_sentence TEXT    NOT NULL,
                doc_source     TEXT    NOT NULL,
                entry_path     TEXT    NOT NULL,
                decl_start     INTEGER,
                decl_end       INTEGER
            )""",
            "CREATE INDEX IF NOT EXISTS types_artifact ON types (artifact_id)",
            "CREATE INDEX IF NOT EXISTS types_binary_name ON types (binary_name)",
            "CREATE INDEX IF NOT EXISTS types_simple_name ON types (simple_name COLLATE NOCASE)",
            "CREATE INDEX IF NOT EXISTS types_display_name ON types (display_name)",
            "CREATE INDEX IF NOT EXISTS types_package ON types (package)",
            """
            CREATE TABLE IF NOT EXISTS supertypes (
                type_id   INTEGER NOT NULL REFERENCES types (id) ON DELETE CASCADE,
                position  INTEGER NOT NULL,
                supertype TEXT    NOT NULL,
                PRIMARY KEY (type_id, position)
            ) WITHOUT ROWID""",
            "CREATE INDEX IF NOT EXISTS supertypes_supertype ON supertypes (supertype)",
            """
            CREATE TABLE IF NOT EXISTS members (
                id             INTEGER PRIMARY KEY,
                type_id        INTEGER NOT NULL REFERENCES types (id) ON DELETE CASCADE,
                position       INTEGER NOT NULL,
                name           TEXT    NOT NULL,
                kind           TEXT    NOT NULL,
                signature      TEXT    NOT NULL,
                parameters     TEXT    NOT NULL,
                visibility     TEXT    NOT NULL,
                is_static      INTEGER NOT NULL,
                deprecated     INTEGER NOT NULL,
                first_sentence TEXT    NOT NULL,
                range_start    INTEGER,
                range_end      INTEGER,
                doc_start      INTEGER,
                doc_end        INTEGER
            )""",
            "CREATE INDEX IF NOT EXISTS members_type ON members (type_id, position)",
            "CREATE INDEX IF NOT EXISTS members_name ON members (name COLLATE NOCASE)");

    private Schema() {
    }
}
