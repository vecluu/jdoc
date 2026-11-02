# jdoc architecture

How the code is built and where things are. Read this before touching code, then the area map for the packages
you work in. What the program does and why is in `docs/SPEC.md`; this file does not repeat it.

## Repository layout

```
pom.xml                    single-module Maven build; version from ${revision}; shaded jar target/jdoc.jar
mvnw, .mvn/                Maven wrapper (always use ./mvnw)
src/main/java/io/github/vecluu/jdoc/   the CLI (packages below); Main.java is the entry point
src/test/java/io/github/vecluu/jdoc/   unit tests (*Test) and ITs (*IT) per package; testsupport/ = shared fixtures
it/sample-project/         small Maven project for the smoke and install tests
dist/                      bin/jdoc (POSIX launcher), install.sh, skill/SKILL.md, AGENTS-snippet.md
scripts/                   assemble.sh, smoke-test.sh, install-test.sh, bench-startup.sh, release-notes.sh
.github/workflows/build.yml  CI and release
docs/                      SPEC, ARCHITECTURE, RELEASING, codemap/ (area maps), decisions/ (rationale records)
tasks/                     orchestration: board and backlog, agent guide, brief template, briefs in flight
```

## Packages

All below `io.github.vecluu.jdoc`. Dependencies point downwards only: nothing imports a package listed further
down the "may depend on" chain (e.g. `index` never imports `cli`). Only the composition root in `cli` may import
every package.

| Package | Responsibility | May depend on | Area map |
| --- | --- | --- | --- |
| `model` | immutable domain values shared by all layers | — | `docs/codemap/foundation.md` |
| `error` | the unchecked `JdocException` hierarchy | — | `docs/codemap/foundation.md` |
| `config` | environment access (`JdocEnvironment`), cache path layout, JDK info | `model`, `error` | `docs/codemap/foundation.md` |
| `query` | query syntax and pure matching (`Type#name(params)`, camel humps, overloads) | `model` | `docs/codemap/foundation.md` |
| `text` | output primitives: pagination, token budget, columns, header line | `model` | `docs/codemap/foundation.md` |
| `module` | find the module, POM key and snapshots, run Maven, `deps.txt`, fallbacks | `config`, `model`, `error` | `docs/codemap/resolution-and-index.md` |
| `index` | SQLite schema, repositories, inter-process lock; the only package with SQL | `config`, `model`, `error` | `docs/codemap/resolution-and-index.md` |
| `javadoc` | Javadoc (doc comments and HTML pages) to compact plain text | `model`, `error` | `docs/codemap/indexing.md` |
| `source` | javac Tree API parsing of `.java` text into descriptors, signature rendering | `model`, `javadoc`, `error` | `docs/codemap/indexing.md` |
| `bytecode` | ASM reading of binary jars and class files | `model`, `error` | `docs/codemap/indexing.md` |
| `decompile` | Vineflower wrapper and decompiled-source cache | `config`, `model`, `error` | `docs/codemap/indexing.md` |
| `indexer` | lazy/forced indexing of artifacts and the JDK, per-type doc-source fallback, refresh | everything above except `query`, `text` | `docs/codemap/indexing.md` |
| `lookup` | read side: type resolution, hierarchy, find, impls, members, docs, on-demand source | `index`, `source`, `decompile`, `query`, `model`, `error` (also allowed: `javadoc`, `bytecode`, `config`) | `docs/codemap/lookup-and-cli.md` |
| `app` | one application service per command, producing a `CommandResult` | all of the above | `docs/codemap/lookup-and-cli.md` |
| `cli` | hand-written parser and help, exit codes, printing, composition root, AOT training entry points | `app`, `config`, `text`; `CompositionRoot` anything | `docs/codemap/lookup-and-cli.md`, training in `docs/codemap/build-and-delivery.md` |

Build, delivery, CI and `testsupport`: `docs/codemap/build-and-delivery.md`.

## Rules

- **Object-oriented design.** Single responsibility, small classes, interfaces at component boundaries (anything that
  touches the filesystem, a process, the database or a jar), constructor injection, composition over inheritance.
  No service locators, static mutable state or singletons. Only `cli.CompositionRoot` wires concrete classes.
- **Immutability.** Domain values are records (or Lombok `@Value`) with defensively copied, unmodifiable collections.
  Public methods return `Optional`, never `null`.
- **Lombok** where it removes noise (`@RequiredArgsConstructor`, `@Slf4j`, `@Builder`, `@Value`). No `@Data` on
  values, no `@SneakyThrows`, no Lombok `val`/`var`.
- **stdout is the product.** Only `cli` writes to `System.out`. Everything else logs with slf4j to stderr, mostly at
  `debug`/`warn`; the default level is `warn`.
- **Errors.** Checked I/O and SQL exceptions are wrapped in the `error` hierarchy at component boundaries (the one
  exception outside it is `decompile.DecompilationException`). Expected outcomes (not found, ambiguous) are values.
- **Environment.** Only `config` reads `System.getenv`, `user.home` or the working directory; everything else gets a
  `JdocEnvironment`, so tests can point cache, repository, `JAVA_HOME` and Maven at temp directories.
- **Hot path.** Lookups (every command except `source` of a type without source text) must not load javac, jsoup, ASM
  or Vineflower; `HotPathClassLoadingIT` enforces it. Indexing components are created lazily.
- **Index format.** Anything that changes what is stored bumps `index.Schema.VERSION` (old indexes are dropped and
  rebuilt); anything that changes the POM analysis bumps `module.ModuleSnapshotCodec.FORMAT_VERSION`.
- **Javadoc** on every public type and non-obvious public method. No commented-out code, no stray TODOs.
- Linux only, UTF-8 everywhere. Base package `io.github.vecluu.jdoc`, main class `io.github.vecluu.jdoc.Main`.

## Dependencies

Fixed; `pom.xml` changes go through the orchestrator. A new runtime dependency also needs a `THIRD-PARTY-NOTICES`
section and `LicenseNoticesIT.OWNERS` entries (`docs/decisions/licensing.md`).

| Dependency | Use |
| --- | --- |
| `jdk.compiler` (JDK) | javac Tree API, parse only (`JavacTask.parse()`), `DocTrees` for Javadoc; no attribution |
| `java.xml` (JDK) | POM files (DOM); no Maven model library |
| sqlite-jdbc | the index; bundles its native libraries |
| jsoup | javadoc-jar HTML |
| ASM | binary class files: type list, signatures, supertypes |
| Vineflower | last-resort decompiling |
| slf4j-api + slf4j-simple | logging, stderr only |
| Lombok (provided) | boilerplate |
| JUnit Jupiter, AssertJ (test) | tests; no Mockito |

## Runtime flows

Chains of key classes, from `Main` to output. The area maps' "Flows" sections hold the detail and the edge cases.

### Every command (shared prefix)

1. `Main` → `cli.JdocCli` parses the arguments (`cli.CommandLineParser`); `cli.CommandRunner` asks
   `cli.CompositionRoot` for the `app.Application`, which builds the services from `JdocEnvironment` and starts
   opening `index.db` on a background thread (the first query waits for it).
2. `cli.JdocCommand.call` → the `app` command → `app.IndexingModuleContext`: `module.ClasspathResolver` →
   `ModuleClasspath` (cache hit: `deps.txt`; miss: Maven). The POM key and reactor come from a per-module snapshot
   while every file the POM analysis touched keeps its stamp.
3. `indexer.ClasspathIndexer` makes sure the JDK and every jar on the classpath is indexed and current (stat, re-index
   only changed jars, under the file lock) → `app.LookupScopes` → fresh `lookup.LookupServices`.
4. The command asks `lookup`, scoped to those artifact ids. Every command except `source` answers from SQLite alone.
5. The `app.CommandResult` (exit code and lines) goes back to `cli.ResultPrinter`, which paginates with
   `text.Paginator` and prints; `Application.close` closes the database.

`--help` and `--version` stop after the parser (`cli.HelpText`, `cli.ManifestVersion`): no application, no index.

### Per command (after the prefix)

| Command | Chain | Map |
| --- | --- | --- |
| `find` | `app.FindCommand` (`query.NamePattern` validates) → `lookup.FindService` → `TypeRepository.listNames` / `MemberRepository.searchByName` | [lookup-and-cli](codemap/lookup-and-cli.md) |
| `class` | `app.ClassCommand` → `lookup.TypeResolver` → `lookup.ClassViewService` (`TypeMembers`, `TypeHierarchy`, `DocLoader`) → `app.ClassViewFormatter` | [lookup-and-cli](codemap/lookup-and-cli.md) |
| `method` | `app.MethodCommand` → `lookup.MemberLookupService` (`TypeResolver`, `query.OverloadMatcher`) → `DocLoader.memberDoc` (`DocInheritance`) → `app.DocLines` | [lookup-and-cli](codemap/lookup-and-cli.md) |
| `source` | `app.SourceCommand` → `MemberLookupService` → `lookup.SourceLoader`: sources → `lookup.JarEntryReader` + `source.MemberSourceSlicer`; otherwise `decompile.DecompiledSourceProvider` → `source.JavaSourceParser` → slice | [lookup-and-cli](codemap/lookup-and-cli.md), [indexing](codemap/indexing.md) |
| `impls` | `app.ImplsCommand` → `TypeResolver` → `TypeHierarchy.subtypes` → `SupertypeRepository.directSubtypesOf` | [lookup-and-cli](codemap/lookup-and-cli.md) |
| `member` | `app.MemberCommand` → `lookup.MemberSearchService` → `MemberRepository.findByName` | [lookup-and-cli](codemap/lookup-and-cli.md) |
| `package` | `app.PackageCommand` → `lookup.PackageService` → `TypeRepository.findByPackage`, `DocLoader.packageDoc` | [lookup-and-cli](codemap/lookup-and-cli.md) |
| `index` | `app.IndexCommand` → `indexer.IndexOptions.indexCommand` → `ClasspathIndexer` → `indexer.IndexReport` | [indexing](codemap/indexing.md) |

### Index build

`indexer.ClasspathIndexer` (stat each jar, take `index.IndexLock` only when something is stale) → per jar
`indexer.JarArtifactIndexer`: `bytecode.ZipBinaryJarScanner` → `indexer.SiblingDocJarLocator` →
`indexer.SqliteIndexStore.replace` (one transaction) running `indexer.TypeDocResolver` per top-level type: sources
(`source.JavaSourceParser`, `javadoc.DocCommentRenderer`), else javadoc jar (`javadoc.JavadocJarReader`), else
bytecode only. The JDK: `indexer.DefaultJdkIndexer` → `indexer.JdkSourceZipProducer`, or `indexer.JrtBytecodeProducer`
without `src.zip`. Detail: `docs/codemap/indexing.md`; storage: `docs/codemap/resolution-and-index.md`.

### Install-time AOT training and launcher start

- **Install:** `dist/install.sh` → `cli.TrainingLibrary` in its own JVM (writes the training library) → a recording
  JVM with `-Djdoc.aotTraining=<workload>` → `Main` → `cli.TrainingWorkload` (one fresh `JdocCli` per workload line)
  → AOT cache and its fingerprint key next to the installed jar.
- **Start:** `dist/bin/jdoc` → `java` from the PATH → the AOT cache if its key matches, else a per-user dynamic CDS
  archive, else none → `-jar jdoc.jar` → `Main`.

Detail: `docs/codemap/build-and-delivery.md`; why: `docs/decisions/startup.md`.

## Change recipes

Files and tests a typical change touches, in order. The area maps' "Extension points" name the exact methods.

**Add a command.**
1. `lookup`: a service if the question is new, wired in `lookup.LookupServices`.
2. `app`: a `LookupCommand` (or a separate class like `app.IndexCommand`) and a method on `app.Application` and
   `app.CommandApplication`.
3. `cli`: a constant in `cli.JdocCommand` (name, argument, help lines, options, `call` branch); `cli.HelpText` picks
   it up.
4. Tests: a `*CommandTest` in `app`, the service's `*Test`, a case in `cli.EndToEndIT`; if it is a lookup, a row in
   `cli.HotPathClassLoadingIT`.
5. `dist/install.sh`: a line in the training `workload`, so the AOT cache covers it.
6. Docs: `docs/SPEC.md` "Commands", `README.md`, `dist/skill/SKILL.md` and `dist/AGENTS-snippet.md`, the
   per-command table above, `docs/codemap/lookup-and-cli.md`.

**Add a CLI option.** `cli.OptionDefinition` → `cli.JdocCommand` (accepted options) → `cli.CommandLineParser` into
`cli.Invocation` → the `app.Application` method. Tests: `CommandLineParserTest`, `HelpTextTest`, `JdocCliTest`, an
`EndToEndIT` case if it changes results. Docs: `docs/SPEC.md` "Commands", `README.md`, the skill if agents should use
it.

**Add a doc source or change the fallback order.** `model.DocSource` (constant, `headerTag()`) →
`indexer.TypeDocResolver` → the `DocSource` switches in `lookup.SourceLoader` and `lookup.DocLoader` → bump
`index.Schema.VERSION`. Tests: `TypeDocResolverTest`, `indexer.ArtifactIndexerIT`, `lookup.LookupIT`; a new fixture
flavour goes into `testsupport.SampleLibrary`. Docs: `docs/SPEC.md` "Doc sources", `docs/codemap/indexing.md`.

**Change the index schema or what is stored.** `index.Schema` (statements, bump `VERSION`) → the repository interface
and its `Jdbc*` class → the writer `indexer.SqliteIndexStore` → the `lookup` readers. Any change to stored text
(signatures, docs, parameter names, entry paths) bumps `VERSION` too. Tests: the repository `*Test`,
`indexer.ArtifactIndexerIT`, `index.BulkInsertIT` for bulk paths. Docs: `docs/codemap/resolution-and-index.md`;
`docs/SPEC.md` "Index, cache and refresh" if users see it.

**Change an output format.** Per command: the `app` command classes, `app.Headers`, `app.LookupOutcomes`,
`app.ClassViewFormatter`, `app.DocLines`. Shared: `text.ResultHeader`, `text.ColumnLayout`, `text.Paginator`.
Signatures are rendered twice and stored, so a signature change touches `source.SignatureFormat` and
`bytecode.MemberDescriptorFactory` alike and bumps `index.Schema.VERSION`. Tests: the `*CommandTest`s,
`text.ResultHeaderTest`, `bytecode.SampleLibraryBytecodeIT`, `cli.EndToEndIT`. Docs: `docs/SPEC.md` "Commands" and
"Signature format", `README.md` examples, the skill if it quotes output.

**Change the launcher or the installer.** `dist/bin/jdoc` (POSIX `sh`) or `dist/install.sh` (`bash`). Bundle contents
change `scripts/assemble.sh`, the expected entries in `scripts/install-test.sh` and `cli.InstallerIT` together. Tests:
`cli.InstallerIT` (also runs shellcheck), then `scripts/install-test.sh` on an assembled bundle. Docs:
`docs/codemap/build-and-delivery.md`, `README.md` install steps, `docs/SPEC.md` "Distribution and startup",
`docs/decisions/startup.md` when the cache strategy changes.

**Add a dependency.** Only with the orchestrator. `pom.xml` → a `THIRD-PARTY-NOTICES` section (and `NOTICE` if the
licence asks for it) → `LicenseNoticesIT.OWNERS` entries → the Dependencies table above. Keep it off the hot path
unless lookups need it (`cli.HotPathClassLoadingIT`). Reasoning: `docs/decisions/licensing.md`.

**Rename or move a class, package or file.** Update this file's package table and the area maps; `CodeMapIT` lists
every stale name with its file and line.

**Cut a release.** `docs/RELEASING.md`.

## Tests

- **Unit tests** `*Test` (surefire) for every non-trivial class: normal, edge and error cases; fast (well under a
  second each). **Integration tests** `*IT` (failsafe) for pieces combining real components, files, jars or SQLite.
  Never start a test class name with `Test` (surefire would run a `Test...IT` as a unit test).
- JUnit Jupiter and AssertJ. No Mockito: small hand-written fakes implementing the boundary interfaces.
- Tests never touch the real `~/.cache/jdoc` or `~/.m2` (use `@TempDir`) and never use the network. The build itself
  may download fixture jars (`pre-integration-test`); tests read them through failsafe system properties.
- Real binary, sources and javadoc jars and fake Maven repositories come from the `testsupport` fixtures; extend them
  rather than writing new builders.
- A test that needs an external tool or file (Maven, shellcheck, fixture jars, `src.zip`) calls
  `testsupport.Prerequisites`: it skips locally when missing and fails in CI (`CI=true`). Never a bare JUnit
  assumption for that.
- Tests that index the whole JDK `src.zip` are ITs only.

## Test map

Which integration test guards which behaviour. Unit tests (`*Test`) sit next to every class and are listed per
package in the area maps. **Slow** marks ITs that index the real JDK, run real Maven or spawn installs and JVMs.

| IT | Guards | Notes |
| --- | --- | --- |
| `LicenseNoticesIT` | the shaded jar's licence files and `THIRD-PARTY-NOTICES` cover every bundled entry and runtime dependency | needs `package` |
| `CodeMapIT` | the docs name existing packages, types and files | reads the working tree only |
| `text.PaginationScenarioIT` | a large result pages within the token budget and concatenates back to the whole | |
| `module.ClasspathResolverIT` | resolution with a fake `mvn`: cache hit, re-resolve after a POM edit, STALE, reactor retry, UNRESOLVED | |
| `module.RealMavenResolutionIT` | the Maven command lines work against a real, offline Maven, including an uninstalled reactor sibling | slow; prerequisites |
| `index.IndexLockIT` | the indexing lock excludes threads and processes | spawns a JVM |
| `index.BulkInsertIT` | bulk inserts of a JDK-sized artifact stay fast | slow |
| `source.SampleLibraryParseIT` | the fixture sources jar parses; stored ranges slice their members | |
| `source.JdkSourceParseIT` | all of `java.base` parses | slow |
| `bytecode.SampleLibraryBytecodeIT` | bytecode signatures equal source-parsed ones, member by member | |
| `bytecode.JdkJmodSmokeIT` | all of `java.base` reads through `jrt:/` | slow |
| `javadoc.JavadocJarIT` | javadoc-jar docs equal the source rendering; real parameter names | |
| `decompile.VineflowerDecompilerIT` | decompiled fixture types parse, are deterministic and cached | |
| `indexer.ArtifactIndexerIT` | the per-type doc-source fallback into real SQLite, supertypes across artifacts, re-index replaces | |
| `indexer.ClasspathIndexerIT` | refresh, force, the lock, concurrent indexers, broken jars, remembered failures | |
| `indexer.JdkIndexerIT` | the full `src.zip` index and the `jrt:/` fallback | slow |
| `indexer.Jdk8JavadocParameterNamesIT` | the legacy (JDK 8) javadoc layout | fixture jars from the build |
| `lookup.LookupIT` | every lookup service on the indexed sample library, including real Vineflower | |
| `lookup.JdkFindIT` | camel-hump `find` on the real JDK | slow |
| `cli.EndToEndIT` | every command and exit code through `JdocCli`: fallbacks, paging, `--from`, refresh, concurrent first runs, the fat jar | slow |
| `cli.HotPathClassLoadingIT` | lookups load no javac, jsoup, ASM or Vineflower | slow; fat jar |
| `cli.TrainingLibraryIT` | the AOT training library generator writes all flavours | |
| `cli.InstallerIT` | install, AOT cache use and fallbacks, training failures, shellcheck of `dist/` and `scripts/` | slow; fat jar |
| `cli.BenchStartupScriptIT` | `scripts/bench-startup.sh` runs and reports every row | slow; fat jar |
| `testsupport.LibraryFixturesIT` | the shared fixtures: jars, fake repository, fake `mvn` | |

Not part of `verify`: `scripts/smoke-test.sh` and `scripts/install-test.sh` (CI runs them after `verify`).

**Fixtures.** `testsupport` (`TestLibrary`, `SampleLibrary`, `FakeMavenRepo`, `TestEnvironments`, `JarWriter`,
`Prerequisites`), described in `docs/codemap/build-and-delivery.md`; the sample library's sources under
`src/test/resources/io/github/vecluu/jdoc/testsupport/sample-lib/`; `it/sample-project` for the scripts; fixture jars
the build downloads (`jdoc.itFixtureJars`). Per-package fakes are listed in the area maps.
