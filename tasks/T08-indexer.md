# T08 — Indexer: lazy artifact indexing, JDK indexing, per-type fallback, refresh

**Packages owned:** `indexer`.
**Depends on:** T01, T03 (store + lock), T04 (source parser), T05 (bytecode + javadoc HTML). Read their notes.
**Spec:** `OUTLINE.md` "Architecture" paragraph, "Index and cache" (lazy indexing, refresh rules), "Doc sources and
fallback" (table and other rules), `jdoc index` row in "Commands and output"; `docs/ARCHITECTURE.md` "Runtime flow".

## Goal

Make sure every artifact on a `ModuleClasspath`, plus the JDK, is present and current in the index, choosing the
best doc source **per type**, and report what was done.

## Scope

1. **`ArtifactIndexer`** (one jar):
   - The binary jar is the list of what exists (T05 scanner). Locate sibling `-sources.jar` and `-javadoc.jar` next
     to the binary jar (same directory, same artifactId/version; classifier jars are their own artifacts).
   - For each **top-level** type (nested types follow their top-level type's choice):
     1. sources jar contains its `.java` file → parse with T04; descriptors from source, `DocSource.SOURCES`.
        Types present in the binary jar but missing from the parsed file (generated classes) still fall through.
     2. else javadoc jar has the type's HTML page → signatures from bytecode (T05), first sentences from HTML,
        `DocSource.JAVADOC`.
     3. else → bytecode signatures, no docs, `DocSource.DECOMPILED` (decompiling itself happens lazily at lookup
        time, not here).
     A `.kt`/`.scala` file in the sources jar does not count as a source.
   - Index all non-anonymous, non-local, non-synthetic types (package-private types too, flagged by visibility,
     because they can sit in a supertype chain), but only public/protected members.
   - Members are matched between source and bytecode only for the source path's own descriptors; no merging needed.
   - Writes everything for one artifact in a single transaction (replace on re-index).
2. **`JdkIndexer`:** indexes `$JAVA_HOME/lib/src.zip` once (key `jdk:<feature version>`), only `java.*`, `javax.*`,
   `jdk.*` packages, excluding `*.internal.*` packages and non-exported looking ones; `DocSource.JDK`. Entry paths
   include the module directory (`java.base/java/util/Optional.java`). If `src.zip` is missing: index from the
   `jrt:/` filesystem with bytecode only (`DocSource.DECOMPILED`) and log one warning. Use parallelism for parsing
   (bounded thread pool), single writer to SQLite. Target: full JDK index in well under a minute on a laptop;
   report the measured time in your notes.
3. **`ClasspathIndexer`** (entry point used by T10):
   - Input: `ModuleClasspath` + force flag. Output: an `IndexedClasspath` value (artifact ids in scope + map from
     artifact id to `ArtifactKey`/label + resolution status) and an `IndexReport` (counts: artifacts indexed /
     reused, types, members; list of types whose doc source is not SOURCES/JDK, grouped by artifact, for the
     missing-sources report of `jdoc index`).
   - Refresh rules: stat each jar; new jar or changed mtime/size → re-index that jar alone; unchanged → reuse.
     `force` → re-index every artifact of this module (not the JDK unless asked explicitly via a separate flag
     or method).
   - Concurrency: take T03's `IndexLock` only when something needs indexing; after acquiring, re-check (another
     process may have done it), then index.
   - Missing jar files on the classpath are skipped with a debug log, never fail the call.

## Tests
- Unit tests with fakes for the store, scanner and parser where it keeps tests focused: per-type fallback decision
  table (all combinations including `.kt` sources and generated classes missing from the sources jar), refresh
  decisions (new/changed/unchanged/force), lock re-check path, report building.
- Integration tests (real SQLite, real jars from T01 fixtures):
  - `ArtifactIndexerIT`: one library with all three doc sources mixed (sources jar missing some files, javadoc jar
    covering some of the rest) → each type gets the right `DocSource` and first sentences.
  - `ClasspathIndexerIT`: index, re-run (nothing re-indexed), touch a jar (only it re-indexed), force (all
    re-indexed), two threads indexing concurrently (no duplicates, no errors).
  - `JdkIndexerIT`: index the real JDK `src.zip` into a temp DB; assert `java.util.Optional` members,
    `java.util.Map$Entry`, an enum and an annotation are present; record time. Skip if `src.zip` is absent.

## Note from T06
The decompiled cache (`decompiled/<g>/<a>/<v>/`) is never validated against the jar. When you re-index an artifact
because its jar changed (typically a SNAPSHOT), delete that artifact's decompiled directory (path from
`CachePaths`) so stale decompiled text is not served. Cover this in `ClasspathIndexerIT`.

## Notes from T04
- `JavaSourceParser` returns types and members of **all** visibilities; filter members with `Visibility.isApi()`.
- It is not thread-safe: use one parser instance per worker thread for the JDK.
- Pass `KnownTypes` built from the binary jar's type list so supertype names resolve exactly instead of guessed.

## Notes from T05
- Scanned types come back as `DocSource.DECOMPILED` with the `.class` entry path; once you choose the doc source,
  override both via the descriptor's `toBuilder()`.
- Package-private types are returned too (keep them for supertype chains); members still need `isApi()` filtering.

## Decisions from the orchestrator (binding)
1. **Supertypes come from bytecode.** For jar artifacts, always take each type's direct supertypes from the binary
   class (T05), even when docs and signatures come from the sources jar. Never store a guessed supertype name.
   For the JDK (no binary jar), first list every type in `src.zip`, build `KnownTypes` from that list and pass it to
   the parser so JDK supertype names resolve exactly. Add a test where a source type extends a type imported via a
   wildcard import from another artifact and assert the stored supertype is exact.
2. **Record `equals`/`hashCode`/`toString`.** Sanctioned change outside your package: make T04's
   `JavaSourceParser` add these three as implicit members of records (flagged implicit like the existing accessors
   and canonical constructor, signatures identical to what T05 renders from bytecode: `boolean equals(Object o)`
   naming per bytecode, etc. — check `SampleLibraryBytecodeIT` comparison and make it include records). Update
   T04's tests and note the change in your notes. Generated/inherited text in javadoc HTML is accepted as-is.
