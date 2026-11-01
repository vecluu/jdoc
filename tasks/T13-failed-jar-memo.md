# T13 — Remember jars that fail to index

**Packages owned:** `indexer`, `index` (storage for the failure record).
**Depends on:** T03, T08 (read their notes).
**Spec:** `OUTLINE.md` "Index and cache" (refresh rules, lock).

## Problem
A jar that fails to index (corrupt, unreadable entries that abort the whole jar) is retried on every call, and the
retry happens under the index lock, so every jdoc call on that module pays for it and serialises other agents.

## Goal
- Record a failure keyed by jar path + mtime + size (in the database, e.g. a small `failed_artifacts` table; bump
  the schema version). While the key matches, skip the jar without taking the lock and include it in the
  `IndexReport`'s failed list as "previously failed".
- A changed jar (mtime or size) or `jdoc index --force` clears the record and retries.
- `jdoc index` output lists failed jars with the one-line reason (check T10's formatter; adjust if needed).

## Tests
- Unit tests for the skip/retry decision (unchanged → skip without lock, changed → retry, force → retry).
- `ClasspathIndexerIT`: a corrupt jar (from the T01 fixtures, e.g. truncated zip) fails once, the second run does
  not re-attempt (assert via a counting fake or timing-free signal), touching the jar retries.
