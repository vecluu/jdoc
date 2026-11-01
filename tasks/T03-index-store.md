# T03 — Index store (SQLite)

**Packages owned:** `index`.
**Depends on:** T01.
**Spec:** `OUTLINE.md` "Index and cache"; `docs/ARCHITECTURE.md` "Database".

## Goal

A small, well-encapsulated persistence layer over `index.db` that the indexer (T08) writes and lookups (T09) read.
No other package may contain SQL.

## Scope

1. **`IndexDatabase`:** opens/creates the SQLite file (path from `CachePaths`), WAL mode, foreign keys on,
   sensible busy timeout so a second process waits instead of failing; schema creation with
   `PRAGMA user_version` (mismatch → drop and recreate). Transaction helper (run a unit of work in one
   transaction, rollback on failure). Closeable.
2. **Repositories** (interfaces + JDBC implementations), one per aggregate:
   - Artifacts: find by key, find by jar path, list by ids, insert/replace, delete (cascade), list all.
   - Types: batch insert for an artifact; find by binary name; find by simple name (case-insensitive); find by
     display name; list by package; list all simple/binary names for an artifact set (for camel-hump search done in
     Java); substring search on names; types per artifact with doc source ≠ SOURCES (missing-sources report);
     package listing.
   - Supertypes: batch insert; direct supertypes of a type; direct subtypes of a binary name (for `impls`).
   - Members: batch insert; members of a type (ordered as declared); find by name (case-insensitive) across an
     artifact set; substring search on member names.
   - Every read method takes the artifact-id scope (a collection of ids). Choose an efficient approach for large
     scopes (e.g. a temporary table or `IN` list chunking) and hide it behind the repository.
   - Mapping rows ↔ `IndexedArtifact`, `IndexedType`, `IndexedMember` (including `ParameterInfo` list encoding and
     optional offsets) is internal to this package.
3. **`IndexLock`:** inter-process exclusive lock on the cache's `lock` file (`FileChannel.lock`, blocking with a
   timeout), plus an in-process guard. Usage pattern for T08: acquire, re-check whether work is still needed,
   index, release. AutoCloseable.
4. **Performance:** batch inserts via prepared statements; indexing the JDK writes ~200k member rows and must
   finish in seconds, not minutes. Index the columns used by lookups.

## Tests
- Unit tests per repository against a real SQLite file in `@TempDir` (SQLite is fast enough to count as unit):
  round-trip of every field, scoping by artifact ids, case-insensitive name lookups, cascade delete, schema-version
  rebuild.
- Integration tests:
  - `IndexLockIT`: two threads and two processes (spawn a small `java` child via the test classpath) contend for the
    lock; the second waits and then proceeds.
  - `BulkInsertIT`: insert ~200k synthetic member rows and assert it completes in a reasonable time bound
    (generous, to avoid flakiness) and that scoped queries stay fast.
