# Code map: resolution and index (`module`, `index`)

Binding rules (package dependencies, immutability, error wrapping, environment access) are in
[`docs/ARCHITECTURE.md`](../ARCHITECTURE.md); this file only maps the code. Paths are relative to
`src/main/java/io/github/vecluu/jdoc/` (tests: `src/test/java/io/github/vecluu/jdoc/`).

## Purpose

`module` turns "the path the user is in" into a `ModuleClasspath`: it finds the enclosing Maven module, derives a
POM key and the reactor, and answers from a cached `deps.txt`. It runs `mvn dependency:list` only on a cache miss and
falls back (STALE, then UNRESOLVED) when Maven fails. `index` is the SQLite cache (`index.db`): schema,
connection and transactions, one repository per table group, and the inter-process indexing lock. No other package
contains SQL. Both packages depend only on `config`, `model` and `error`.

## Key types

### `module` (entry point `module.ClasspathResolver`)

Resolution pipeline
- `ClasspathResolver` — interface: `resolve(Path start)` → `ModuleClasspath`; throws `ResolutionException` (exit 3).
- `MavenClasspathResolver` — the only implementation, `@Builder`. `create(env)` wires the real components;
  `create(env, MavenInvoker, Clock)` is for tests. It owns the cache-hit / Maven / STALE / UNRESOLVED decision.
- `ModuleLocator` — nearest directory upwards with a `pom.xml` file. It returns the real path, and that path is the module identity everywhere.

POM analysis (POM key and reactor)
- `PomAnalyzer` — interface `analyze(moduleDir)` → `PomAnalysis(PomKey key, Reactor reactor)`.
- `ParsingPomAnalyzer` — parses: `PomKeyCalculator` + `ReactorDetector`.
- `SnapshotPomAnalyzer` — the hot-path wrapper. It reuses a stored `ModuleSnapshot` while every path the last
  parse touched still has the same `PathStamp`. Otherwise it parses under `RecordingPomFiles.record` and stores a new
  snapshot (only once the touched files have been unchanged for `SETTLE_TIME`, 2 s).
- `PomKeyCalculator` — SHA-256 over the module dir path, the module POM and the local ancestor POMs (path + content
  hash). A SNAPSHOT parent from the repository adds its repo POM's mtime → `PomKey(value, List<PomFingerprint>)`.
- `ReactorDetector` — walks up through aggregators (parent-dir POM listing this dir) and local parents, then down
  through `<modules>`/`<subprojects>` (profiles included) → `Reactor(rootDirectory, Set<"g:a">)`.
- `PomHierarchy` — the single gateway to POM files (`files()`, `read`, `tryRead`, `localChain` → `LocalPomChain`,
  `localParent`, which accepts the parent only if its coordinates match).
- `PomReader` — secure DOM read (no DOCTYPE, external entities or XInclude; namespace optional) → `PomModel`.
  It interpolates the POM's own `<properties>` and `project.*`/`parent.*` only. The parser factory is created lazily.
- Values: `PomModel`, `ParentReference` (`relativePath` empty = `<relativePath/>`), `DeclaredDependency`
  (direct `<dependencies>`, used for sibling detection and the UNRESOLVED guess), `LocalPomChain`.
- File access: `PomFiles` (interface), `NioPomFiles`, `RecordingPomFiles` (remembers every path asked about while
  recording; thread-confined).
- Stamps: `PathStamper` / `NioPathStamper`. A stamp is `absent`, `dir`, or `file:size:mtimeNs:ctimeNs:dev:ino` from the
  unix attribute view, else basic attributes plus the file key. The value type is `PathStamp`.
- Snapshot storage: `ModuleSnapshot`, `ModuleSnapshotCodec` (tab-separated lines, `FORMAT_VERSION` 1),
  `ModuleSnapshotStore` / `FileModuleSnapshotStore` (`<cache>/module-snapshots/<fnv1a64(moduleDir)>.txt`).

Maven
- `MavenInvoker` (interface) / `ProcessMavenInvoker` — child process with merged output captured to a debug log and stdin
  closed. It sets `JAVA_HOME` and kills the process after `DEFAULT_TIMEOUT` (5 min). Values: `MavenCommand`, `MavenResult`
  (`summary()` = first `[ERROR]` line).
- `MavenDependencyLister` — builds and runs `-o -q -B dependency:list -DincludeScope=test
  -DoutputAbsoluteArtifactFilename=true -DoutputFile=…`. Strategy: plain run in the module dir; the reactor form
  (`test-compile` + `REACTOR_ARGUMENTS` skip flags + `-pl <rel> -am` from the root) runs first if `reactorFirst`,
  else after the plain run fails. If the output contains `No plugin found for prefix 'dependency'`, it retries with the
  goal from `DependencyPluginLocator`. Success = exit 0 **and** the output file exists.
- `DependencyPluginLocator` — newest local `maven-dependency-plugin` with a jar → fully qualified `:list` goal.
- `DepsFileParser` — parses `deps.txt` lines (`g:a:type[:classifier]:v:scope:/abs/path`, `(optional)`, `-- module`).
  Non-jar types, relative paths and missing jars are skipped.

Cache and fallbacks
- `ModuleCache` / `FileModuleCache` — `modules/<key>/deps.txt` + `meta.json`. Maven writes to
  `deps.txt.<uuid>.tmp`; `commit` publishes by atomic rename; `latestFor(moduleDir)` scans all `meta.json` for the
  STALE fallback → `CachedDependencyList`.
- `ModuleMeta` + `ModuleMetaCodec` (flat JSON via package-private `FlatJson`).
- `RepositoryVersionGuesser` + `MavenVersionComparator` (simplified Maven ComparableVersion) — the UNRESOLVED fallback: the
  newest version dir in the local repo that holds the declared jar.

### `index` (entry point `index.IndexDatabase`)

- `IndexDatabase` — one JDBC connection (thread-confined). `open(CachePaths)`, `open(Path, Duration busyTimeout)` and
  `openInBackground(CachePaths)` (a daemon thread; the first `connection()` waits and rethrows). It sets WAL,
  `foreign_keys`, `synchronous=NORMAL` and a 60 s busy timeout, and checks `PRAGMA user_version`. `inTransaction` /
  `runInTransaction` use `BEGIN IMMEDIATE`; nested calls join the outer transaction.
- `Schema` (package-private) — DDL list + `VERSION` (currently 4). Tables: `artifacts`, `failed_artifacts`,
  `types`, `supertypes`, `members`, `type_docs`, `member_docs`, `package_docs`.
- Repositories (interface / JDBC class, each built from an `IndexDatabase`):
  - `ArtifactRepository` / `JdbcArtifactRepository` — `save` deletes the row with the same key (cascade), then
    inserts (`AUTOINCREMENT` ids are never reused); `findByKey`, `findByJarPath`, `findByIds`, `findAll`, `delete`.
  - `TypeRepository` / `JdbcTypeRepository` — `insertAll(artifactId, descriptors)` also writes supertypes. Scoped reads:
    by binary, simple (NOCASE), display name or package, `findNested` (range scan `outer$`…`outer%`), `searchByName`,
    `listNames` (all, or prefiltered by a `LIKE` subsequence on `NameColumn`), `listPackages`. Others:
    `findWithoutSources`, `countByArtifact`. Returns `IndexedType` with full supertypes, or `TypeRef`.
  - `SupertypeRepository` / `JdbcSupertypeRepository` — read-only: `supertypesOf`, `directSubtypesOf` (direct edges).
  - `MemberRepository` / `JdbcMemberRepository` — `insertAll(Map<typeId, List>)` (batches of `BATCH_SIZE` 1000),
    `membersOf`, `findByName` (NOCASE), `searchByName`, `listNames(scope, subsequence)`, `findByNames`.
  - `DocRepository` / `JdbcDocRepository` — rendered `RenderedDoc` blobs per type, member and package. Large batches are
    encoded in parallel.
  - `FailedArtifactRepository` / `JdbcFailedArtifactRepository` — `failed_artifacts` rows (`FailedArtifact`, with
    `matches(path, mtime, size)`).
- `IndexLock` (interface, `Lease`) / `FileIndexLock` — a fair in-process ReentrantLock plus an OS FileChannel.tryLock
  on `<cache>/lock`, polled with backoff up to `DEFAULT_TIMEOUT` (10 min). Reentrant per thread.
- Package-private helpers: `SqlRunner` (statements, SQLException → `IndexException`), `Rows`, `IdList` (scope as one
  JSON-array parameter: `col IN (SELECT value FROM json_each(?))`, plus `LIKE` pattern escaping), `ParameterCodec`
  (`name␟type␟erased` joined by `␞`), `DocCodec` (flag byte + varint/UTF-8 body, raw deflate when ≥ 160 bytes and smaller).

## Collaborations

- **Calls out:** `config.JdocEnvironment` (working dir, local repo + `localRepositoryExplicit`, Maven executable,
  `JAVA_HOME`), `config.CachePaths` (`depsFile`, `metaFile`, `modulesDirectory`, `moduleSnapshotsDirectory`,
  `indexDb`, `lockFile`), `model` values, `error.ResolutionException` / `CacheIoException` / `IndexException`.
- **Called by:**
  - `cli.CompositionRoot` — `IndexDatabase.openInBackground` (started first), all JDBC repositories,
    `FileIndexLock`, `MavenClasspathResolver.create(env)`. The `Application` it returns closes the database.
  - `app.IndexingModuleContext` — `ClasspathResolver.resolve(from or working dir)`, then hands the classpath to the indexer.
  - `indexer.SqliteIndexStore` (the only writer: `ArtifactRepository`, `TypeRepository`, `MemberRepository`,
    `DocRepository`, `FailedArtifactRepository`, `IndexDatabase.inTransaction`); `indexer.ClasspathIndexer`
    (`IndexLock`, `FailedArtifact`).
  - `lookup` (read side): `LookupServices`, `ScopedTypes`, `TypeHierarchy`, `TypeMembers`, `FindService`,
    `MemberSearchService`, `DocLoader`, `ArtifactFiles`.

## Flows

**Resolve a module (every command):** `IndexingModuleContext` → `MavenClasspathResolver.resolve(start)`:
1. `ModuleLocator.locate` → real module dir.
2. `SnapshotPomAnalyzer.analyze`: `FileModuleSnapshotStore.read` → same local repo and all stamps equal → stored
   `PomAnalysis` (no XML parser, no SHA-256). Otherwise `ParsingPomAnalyzer` under recording → `store.write`.
3. `FileModuleCache.readDependencyList(key)` hit → `DepsFileParser.parse` → drop reactor modules → RESOLVED.
4. Miss → `newOutputFile` → `MavenDependencyLister.list(moduleDir, reactorRoot, dependsOnSibling, out)`. The sibling
   test reads the declared deps of the local POM chain. On success: `commit` (`deps.txt`, then `meta.json`) → RESOLVED.
5. Failure → `discard` → `latestFor(moduleDir)` → STALE + `warning: mvn failed, using classpath resolved at …`.
6. No old list → `RepositoryVersionGuesser.guess` over the declared non-reactor deps → UNRESOLVED + warning. If the
   guess is empty while a jar was declared → `ResolutionException`.

**Open the index:** `openInBackground` → thread: `CachePaths.createRoot` → sqlite-jdbc connect →
`retryWhileBusy(enableWal)` → `retryWhileBusy(initialiseSchema)`. If `user_version` ≠ `Schema.VERSION`: drop every table,
run the DDL and set the version, all in one `BEGIN IMMEDIATE` transaction.

**Index one artifact (writer side):** `ClasspathIndexer` takes `FileIndexLock.acquire()` →
`SqliteIndexStore.replace` → one `IndexDatabase.inTransaction`. Inside: `ArtifactRepository.save` (cascade-deletes the old rows),
`FailedArtifactRepository.delete`, then per producer batch `TypeRepository.insertAll` → `MemberRepository.insertAll`
→ `DocRepository.insertTypeDocs` / `insertMemberDocs`, plus `savePackageDoc` per package.

**Read (lookups):** `lookup` services call scoped repository methods with the classpath's artifact ids plus the
JDK id. An empty scope returns empty without a query.

## Tests

- `module/*Test` (20 classes, ~130 tests, fast): one per class. Fakes: `FakeMavenInvoker` (scripted results,
  records commands), `PomFixtures` (writes POMs). `SnapshotPomAnalyzerTest` uses a fresh parse as the oracle and covers
  edits with restored mtime, replaced files, appearing aggregators, SNAPSHOT parents and the racy guard.
  `DepsFileParserTest` also parses real plugin output (`src/test/resources/io/github/vecluu/jdoc/module/real-dependency-list.txt`).
- `module/ClasspathResolverIT` — fake `mvn` shell script via `JdocEnvironment`: cache hit avoids a 2nd run, POM edit
  re-resolves, failure → STALE, reactor retry from the root, no list → UNRESOLVED guess.
- `module/RealMavenResolutionIT` — a **real Maven**, offline, temp multi-module project. It covers an uninstalled
  sibling resolved through the reactor command. It skips locally and fails in CI when prerequisites are missing
  (`testsupport.Prerequisites`); this is the slowest test in the area (several Maven runs).
- `index/*Test` (12 classes, ~75 tests) against real SQLite files in `@TempDir`; `IndexFixtures` builds rows.
  `IndexDatabaseConcurrencyTest`: 8 threads open a fresh file at once (the SQLITE_BUSY regression).
- `index/IndexLockIT` — thread vs thread and process vs process; the child process is `index.LockHolderMain`, started with
  the test classpath. `index/BulkInsertIT` — 20k types / 200k members (time bound 30 s; the slowest index test).
- Elsewhere: `indexer.ClasspathIndexerIT`, `lookup.JdkFindIT` and `cli.EndToEndIT` exercise the real store end to end.
  `EndToEndIT` runs two concurrent first runs; `cli.HotPathClassLoadingIT` asserts what stays unloaded on a lookup.

## Extension points

- **Schema change:** edit `Schema.STATEMENTS` and bump `Schema.VERSION` (Javadoc lists v2–v4). Also bump it when the
  *stored content* changes (v4 changed only signatures). Old files are dropped and rebuilt; there are no migrations.
- **Analysis change:** if `PomKeyCalculator` or `ReactorDetector` compute something else or read other files, bump
  `ModuleSnapshotCodec.FORMAT_VERSION`. Otherwise stale snapshots keep serving the old analysis.
- New POM inputs must go through `PomHierarchy.files()`. A file read around it is not stamped, so its changes go unnoticed.
- `MavenInvoker` (process vs fake), `ModuleCache`, `ModuleSnapshotStore`, `PathStamper`, `PomFiles`, `PomAnalyzer`,
  `IndexLock` and all repository interfaces are the seams for alternative implementations and test fakes.
- Maven command tweaks: `MavenDependencyLister.withExtraArguments` (`-Dmaven.repo.local` when `JDOC_M2_REPO` is
  explicit), `withFallbackGoal`, `REACTOR_ARGUMENTS`.
- New scoped queries: add them to the repository interface and use `IdList.IN_LIST` with `IdList.json(scope)`. Return
  empty for an empty scope.

## Gotchas

- **`deps.txt` means "Maven succeeded for this key".** Never write it directly. The temp file + atomic rename keep
  concurrent processes from seeing partial lists, and a failed run leaves nothing, so the next call retries Maven.
- **Cache hits trust `deps.txt`.** A jar deleted from `~/.m2` is silently dropped by the parser (no re-resolve).
  `settings.xml`, BOM imports and non-SNAPSHOT repository parents are not part of the POM key.
- The POM key includes the module **path**, so two checkouts never share `modules/<key>/`. Old key dirs are never
  pruned (they feed the STALE fallback).
- The STALE fallback matches `meta.json` by the module's *real* path; `ModuleLocator` must keep returning real paths.
- The racy-timestamp guard: right after an edit (< 2 s), every call parses and nothing is stored. This is intended.
  ctime in the stamp catches same-size edits with a restored mtime.
- Reactor modules are removed from every classpath (matched by `groupId:artifactId`, any version). The reactor command
  exists because `-pl x -am` without `test-compile` cannot resolve uninstalled siblings in Maven 3.
- Maven output never reaches stdout: `ProcessMavenInvoker` captures it, and warnings travel only in `ModuleClasspath.warnings()`.
- `IndexDatabase` is **one connection, one thread**. The indexer's parallel parsing feeds a single writer thread.
- WAL is switched on *after* connecting, and open steps are retried on SQLITE_BUSY/LOCKED. Setting WAL in
  sqlite-jdbc config broke concurrent first opens of a fresh file.
- `openInBackground` looks up its logger lazily. A static `@Slf4j` field would initialise slf4j (~12 ms) before the
  thread starts.
- `JdbcTypeRepository.insertAll` assigns ids as `max(id)+1…`. That is safe only because `BEGIN IMMEDIATE` holds the
  write lock. Never insert types outside `inTransaction`.
- `insertMemberDocs` finds members by `(type_id, position)`. Positions restart at 0 on each `insertAll` call, so
  insert a type's members exactly once, in the same order as the doc list.
- `failed_artifacts` is keyed by artifact key with no foreign key. `SqliteIndexStore.replace` clears it explicitly.
- `LIKE`-based searches and prefilters fold ASCII case only. The NOCASE indexes (`types_simple_name`, `members_name`)
  are used only when the query says `COLLATE NOCASE`. `findByNames` adds a redundant NOCASE `IN` for that reason.
- `findWithoutSources` treats both SOURCES and JDK doc sources as "has sources".
