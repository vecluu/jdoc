# Code map: foundation (`config`, `model`, `error`, `query`, `text`)

Paths are relative to `src/main/java/io/github/vecluu/jdoc/` (tests: `src/test/java/io/github/vecluu/jdoc/`) unless they start
with `src/`. Binding rules, runtime flows, change recipes and the test map are in
[`docs/ARCHITECTURE.md`](../ARCHITECTURE.md); behaviour is in [`docs/SPEC.md`](../SPEC.md) (signature format, exit codes). Neither is repeated here.

## Purpose

The bottom layer every other package builds on. `model` holds the immutable domain values (coordinates, type names,
type/member descriptors, rendered docs, index rows); `config` is the only gateway to the process environment and the
cache layout; `error` is the unchecked exception hierarchy. `query` (parsing and matching what the user typed) and
`text` (result text, pagination, column layout, header line) are pure, I/O-free helpers for the read side.

## Key types

### `model` (depends on nothing in jdoc)

- `model.Gav` — Maven coordinates (`groupId`, `artifactId`, `version`, `Optional` classifier, `type` default `jar`).
  `toString()` = `g:a:v`; `canonical()` is the unique parseable form (adds type/classifier when not a plain jar) and
  `parse()` is its inverse; `coordinates()` = `g:a` (reactor matching); `isSnapshot()`/`baseVersion()` handle
  timestamped snapshots; repository-relative paths (`repositoryPath()`, `pomPath()`, `groupPath()`, ...).
- `model.ArtifactKey` — sealed interface, `ArtifactKey.Maven(gav)` | `ArtifactKey.Jdk(featureVersion)`.
  `keyString()` (`Gav.canonical()` or `jdk:25`) is the database unique key, `parse()` its inverse; `label()` is the
  user-facing text (`commons-lang3 3.17.0`, `jdk 25`) shown in result headers.
- `model.TypeName` — a binary name (`java.util.Map$Entry`) and everything derived from it: `simpleNames()`,
  `simpleName()`, `displayName()`, `relativeName()`, `outerName()`, `topLevel()`, `nested()`, `internalName()`,
  and container entry paths `sourceEntryPath()` (top-level `.java`), `classEntryPath()`, `javadocPagePath()`.
  Factories: `of(binary)`, `of(pkg, simpleNames)`, `fromInternalName`, `fromClassEntryPath`, `fromDisplayName`.
- `model.TypeDescriptor` / `model.MemberDescriptor` — everything the index stores about a type / member (Lombok
  `@Builder(toBuilder = true)`). `MemberDescriptor` adds `arity()`, `erasedParameterTypes()`, `erasedKey()`
  (`name(Erased,Types)`, overload identity: equal keys override/hide) and `reference()` (`pkg.Type#name(...)`).
- `model.ParameterInfo` — name, declared type text, erased simple type used for overload matching.
- `model.RenderedDoc` — plain-text doc: description, first sentence, block-tag lines, `inheritsDoc` flag;
  `RenderedDoc.missing()` is the shared "no comment" value.
- `model.SourceRange` — half-open `[start, end)` character range; `slice(text)` cuts source by stored offsets.
- `model.IndexedArtifact`, `model.IndexedType`, `model.IndexedMember` — rows of `artifacts`/`types`/`members`
  (database id + key or descriptor).
- `model.ResolvedDependency` (`Gav`, `Scope`, absolute jar) and `model.ModuleClasspath` (module dir, POM key,
  dependencies, `ResolutionStatus`, warning lines) — the output of `module`.
- Enums: `model.TypeKind` (`keyword()`, `@interface` for annotations), `model.MemberKind` (`isExecutable()`),
  `model.Visibility` (`isApi()` = public/protected, `keyword()`), `model.DocSource` (SOURCES, JAVADOC, DECOMPILED,
  JDK; `headerTag()` gives `[from javadoc jar]` / `[decompiled, no docs]`), `model.Scope` (`parse()` → `Optional`,
  `mavenName()`), `model.ResolutionStatus` (RESOLVED, STALE, UNRESOLVED).
- `model.Checks` — package-private argument checks used by the compact constructors.

### `config` (depends on `model`, `error`)

- `config.JdocEnvironment` — record: cache dir (`JDOC_CACHE`), local repo (`JDOC_M2_REPO`), Java home
  (`JAVA_HOME`, else `java.home`), Maven command (`JDOC_MVN`, else `mvn`), working dir (`user.dir`),
  `localRepositoryExplicit` (true when `JDOC_M2_REPO` is set, so `module` passes the repo to Maven).
  `fromSystem()` for production, `from(env, properties)` and `builder()`/`toBuilder()` for tests; `cachePaths()`.
- `config.CachePaths` — pure path accessors for the cache layout (`indexDb()`, `lockFile()`, `depsFile(key)`,
  `metaFile(key)`, `moduleSnapshotsDirectory()`, `decompiledDirectory(gav)`, `decompiledSource(gav, type)`);
  directories are created only by the `create...` methods, which throw `CacheIoException`.
- `config.JdkInfo` (Java home, feature version, optional `lib/src.zip`; `artifactKey()` = `jdk:<n>`) read by
  `config.JdkInfoReader` from the JDK's `release` file (`25.0.1` → 25, `1.8.0_x` → 8).

### `error`

- `error.JdocException` — unchecked base; its message is the one user-facing line printed after `error: `.
- Subclasses by failure domain: `error.ConfigurationException` (unusable environment / not a JDK),
  `error.ResolutionException` (module or classpath undeterminable, no fallback), `error.IndexException` (SQLite,
  schema, lock), `error.JarReadException` (corrupt/unreadable jar, zip or class), `error.SourceParseException`
  (source or doc page unparseable), `error.CacheIoException` (cache file I/O). `decompile.DecompilationException`
  is the one subclass outside this package.

### `query` (depends on `model`; no index access, failures are values)

- `query.ParseResult` — sealed `Success(result)` | `Failure(message)`; every parser returns it, none throws on bad
  input. `map`, `value()`, `error()`, `orElseThrow()`.
- `query.TypeReferenceParser` → `query.TypeReference` (input, name pieces, ordered `query.TypeCandidate` list).
  A dotted name is ambiguous (package vs. nesting), so it yields several candidates: `TypeCandidate.Relative`
  (simple-name chain in any package; added when there is one piece or the first piece is upper-case) and
  `TypeCandidate.Exact` (one binary name per package/nesting split, conventional split first). Type arguments are
  ignored.
- `query.MemberReferenceParser` → `query.MemberReference` for `Type#name[(params)]`: no parentheses = all
  overloads (`allOverloads()`), `()` = zero parameters; `Type#Type` and `Type#<init>` set `constructor` and use the
  simple type name as `name` (same convention as `MemberDescriptor`).
- `query.ParameterTypes` — normalizes parameter text for matching: generics erased, `...` → `[]`, qualified or
  nested names reduced to the simple name, a trailing parameter name tolerated. `normalize` (strict, for user
  input) and `normalizeLenient` (for index-side text).
- `query.OverloadMatcher` — does a `MemberDescriptor`/`ParameterInfo` list match a `MemberReference`: name,
  constructor-ness, arity, and each queried type equals the normalized erased type or the normalized declared type
  (so `T` and `Object` both match a type-variable parameter, `Entry` and `Map.Entry` both match `Map.Entry`).
- `query.NamePattern` — `jdoc find` matching: `match(name)` → `Optional<query.NameMatch>` with a
  `query.MatchKind` (EXACT, PREFIX, CAMEL_HUMPS, SUBSTRING, best first); `NameMatch` orders by kind then shorter
  name. Camel humps only when the pattern has an upper-case letter (`usesCamelHumps()`); implemented as a DP over
  word starts (upper case, digit-run starts, after `_`/`$`/`.`/digits).
- Package-private helpers: `query.GenericsEraser` (strip `<...>`, detect unbalanced), `query.Identifiers`.

### `text` (depends on `model`; no I/O)

- `text.TextResult` — one header line + ordered `text.TextBlock`s (lines kept together on one page where
  possible); `TextResult.builder(header)` with `line`/`lines`/`block`/`blankLine`. Lines must not contain `\n`/`\r`.
- `text.Paginator` — cuts a `TextResult` to a `text.TokenBudget` (`TokenBudget.DEFAULT_MAX_TOKENS`, 4 chars per
  token, newlines counted). Fits → one page, no footer; else each `text.Page` repeats the header and ends with a
  `text.PageFooter` line (wording in `docs/SPEC.md` "Commands"). `page(result, n, cmd)` returns
  `text.PageOutcome` (`Shown` | `OutOfRange` with the user message).
- `text.ColumnLayout` — two-column alignment (`ColumnLayout.Row`), left column padded to the widest entry up to
  `DEFAULT_MAX_LEFT_WIDTH`, then `DEFAULT_GAP`, no trailing spaces; widths in code points.
- `text.ResultHeader` — the type header line: `displayName<T>  modifiers keyword  (artifact label)  [doc-source
  tag]  [version unresolved]`, built from a `TypeDescriptor` and `ArtifactKey.label()`.
- `text.Lines` — package-private single-line check and printed-size helper.

## Collaborations

- **Calls out:** nothing outside the area except `config` → `model`/`error` and `query`/`text` → `model`. The area
  has no I/O beyond `config` (reading the JDK `release` file, creating cache directories).
- **`model`** is imported by every other package. `index` persists its values (`ArtifactKey.keyString()` as the
  artifact unique key, enum `name()`s as column text, `ParameterInfo` via `index.ParameterCodec`, `RenderedDoc` via
  `index.DocCodec`); `source`, `bytecode`, `javadoc` produce `TypeDescriptor`/`MemberDescriptor`/`RenderedDoc`.
- **`config`:** `Main` builds `JdocEnvironment.fromSystem()` and hands a supplier to `cli.JdocCli.forEnvironment`;
  `cli.CompositionRoot.create(environment)` derives `CachePaths` and calls `JdkInfoReader`. `CachePaths` is passed
  to `index.IndexDatabase`, `module.FileModuleCache`, `module.FileModuleSnapshotStore`,
  `decompile.DecompiledSourceCache`, `indexer.DecompiledDirectoryEvictor`; `JdkInfo` to `indexer.DefaultJdkIndexer`.
  `module.MavenClasspathResolver`/`module.ProcessMavenInvoker` read the Maven command and repository from the env.
- **`error`:** thrown at component boundaries in `module`, `index`, `bytecode`, `javadoc`, `source`, `indexer`,
  `decompile`, `lookup`; caught in `cli.JdocCli` and formatted by `cli.FailureReporter` (exit 3).
  `indexer.ClasspathIndexer` catches `JdocException` per jar so one bad jar does not fail the command.
- **`query`** is used only by `lookup` (`lookup.TypeResolver` ← `TypeReferenceParser`; `lookup.MemberLookupService`
  ← `MemberReferenceParser` + `OverloadMatcher`; `lookup.OverrideMatcher` ← `ParameterTypes`;
  `lookup.FindService` ← `NamePattern`) and by `app.FindCommand` (validates the pattern with `NamePattern.parse`).
- **`text`** is used by `app` (every `*Command` builds a `TextResult`; `app.Headers` wraps `ResultHeader`;
  `app.ClassViewFormatter` and list commands use `ColumnLayout`; `app.CommandResult` carries the `TextResult`) and
  by `cli` (`cli.JdocCli` owns the `Paginator`, `cli.ResultPrinter` prints the chosen `Page` or the
  `OutOfRange` message).

## Flows

- **Start-up:** `Main` → `JdocEnvironment.fromSystem()` (env + system properties, `~` expansion, relative paths
  against `user.dir`) → `cli.CompositionRoot.create` → `environment.cachePaths()` and
  `JdkInfoReader.read(javaHome)` → `JdkInfo.artifactKey()` identifies the JDK artifact in the index.
- **Type argument → type:** `lookup.TypeResolver.resolve(input)` → `TypeReferenceParser.parse` →
  `ParseResult.Failure` becomes `TypeLookup.Invalid`; else every `TypeCandidate.Exact` is tried by binary name, then
  every `TypeCandidate.Relative` by simple name (`TypeCandidate.simpleName()` hits the index column) filtered with
  `Relative.matches(TypeName)`.
- **Member argument → members:** `lookup.MemberLookupService` → `MemberReferenceParser.parse` → type part as above
  → the type's members from `lookup.TypeMembers` (own and inherited) filtered by `OverloadMatcher.matches(reference, descriptor)`.
- **Find pattern:** `NamePattern.parse` (blank → `ParseResult.Failure`) → `NamePattern.match` per name → hits ranked
  by `NameMatch`; `usesCamelHumps()` tells `lookup.FindService` which index prefilter to use.
- **Output:** `app` command → `TextResult` (header from `ResultHeader.line()`, rows via `ColumnLayout`) →
  `app.CommandResult` → `cli.ResultPrinter` → `Paginator.page(result, --page, command line)` → `Page.text()` to
  stdout, or `PageOutcome.OutOfRange.message()` with exit 1.

## Tests

All under `src/test/java/io/github/vecluu/jdoc/<package>/`; all unit tests are pure and fast.

- `model`: `GavTest`, `TypeNameTest` (binary-name `$` rules, entry paths), `ArtifactKeyTest`, `DescriptorsTest`
  (descriptor validation, `erasedKey`), `ValuesTest` (enums, small records).
- `config`: `JdocEnvironmentTest` (defaults, `~`, relative paths, blank = unset, `localRepositoryExplicit`),
  `CachePathsTest`, `JdkInfoReaderTest` (temp `release` files).
- `error`: `JdocExceptionTest`.
- `query`: `TypeReferenceParserTest` (candidate order), `MemberReferenceParserTest`, `ParameterTypesTest`,
  `OverloadMatcherTest`, `ParseResultTest`, `NamePatternTest` (hump cases, lower-case patterns not humps).
- `text`: `PaginatorTest`, `PageFooterTest`, `TokenBudgetTest`, `TextResultTest`, `ColumnLayoutTest`,
  `ResultHeaderTest`.
- Only IT: `text.PaginationScenarioIT` — a class view several pages long under the default budget: every page within
  budget and pages concatenate back to the full body. Fast (no jars, no database).
- Test environments for other packages are built with `testsupport.TestEnvironments` (a `JdocEnvironment` rooted in
  a `@TempDir`), which is why `JdocEnvironment.from(env, properties)` must stay side-effect free.

## Extension points

- **New environment setting:** add a component to `JdocEnvironment` and read it in `from(...)` (update
  `JdocEnvironmentTest`; consumers get it via the composition root). Never read `System.getenv` elsewhere.
- **New cache file or directory:** add an accessor (and a `create...` method if needed) to `CachePaths`.
- **New failure category:** subclass `JdocException` (here or, for a package-specific one, in the owning package
  as `decompile` does); `cli.FailureReporter` needs no change.
- **New doc origin / header tag:** add a `DocSource` constant with its `headerTag()`; `ResultHeader` picks it up.
  The constant name is stored in the `types.doc_source` column, see Gotchas.
- **New query syntax:** a parser returning `ParseResult`; new match kinds go into `MatchKind` (declaration order is
  the ranking order).
- **Output shape:** `TokenBudget` value (wired in `cli.JdocCli`), `ColumnLayout` cap/gap constructor,
  `PageFooter` wording.

## Gotchas

- **Persisted names.** `index` stores enum `name()`s (`TypeKind`, `MemberKind`, `Visibility`, `DocSource`) and
  reads them back with `valueOf`; `ArtifactKey.keyString()` / `Gav.canonical()` are the artifact unique key and are
  parsed back with `ArtifactKey.parse`. Renaming a constant or changing the canonical form breaks existing
  `index.db` files unless `index.Schema.VERSION` is bumped (which drops and rebuilds).
- **`$` in `TypeName`.** `$` is a nesting separator only between two non-empty pieces; a top-level class with `$`
  in the middle of its name is misread as nested (documented limitation). `TypeReferenceParser` uses the same rule.
- **Constructors** are named by the simple type name, never `<init>`; `MemberDescriptor` rejects anything else and
  `MemberReferenceParser` maps `<init>` to it. A method that happens to have the type's simple name is therefore
  parsed as a constructor reference.
- **`Gav.toString()` vs `canonical()`.** `toString()` drops type and classifier; use `canonical()`/`keyString()`
  wherever uniqueness matters. Classified jars get their own decompiled directory (`<version>-<classifier>`).
- **Candidate order vs. resolution order.** `TypeReferenceParser` puts `Relative` first, but
  `lookup.TypeResolver` tries all `Exact` candidates before any `Relative` one; the list order only matters within
  each kind.
- **Camel humps** apply only to patterns with an upper-case letter: `map` does not match
  `MutableAnyPair`, and the hump check runs after EXACT/PREFIX so prefix hits rank higher.
- **Overload matching is case-sensitive** on normalized simple names and does not check the declaring type; callers
  scope by type first. Parentheses never match fields or enum constants.
- **Pagination:** only a single line longer than a whole page can exceed the budget; header and footer count
  against it; footer room is reserved for the widest page number (re-split when the page count gains a digit). The
  `--page` option is stripped from the echoed command line by `PageFooter.withoutPageOption`.
- **Text values reject line breaks** (`TextBlock`, `TextResult`, `Page`, `ColumnLayout.Row`, `ResultHeader`):
  multi-line doc text must be split into lines before it reaches `text`.
- **Paths in `JdocEnvironment` are always absolute and normalized**; `JDOC_MVN` stays a bare command (resolved on
  `PATH` by the process) unless it contains `/` or starts with `~`. Blank variables count as unset.
- **`CachePaths.moduleDirectory`** only accepts `[A-Za-z0-9_-]+` keys (path-traversal guard).
- **No logging, no stdout** anywhere in the area; `error` messages must be one concise line (the CLI collapses
  them to one line anyway).
