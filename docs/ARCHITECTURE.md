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

## Runtime flow (one call)

1. `Main` → `cli.JdocCli` parses the arguments; `cli.CompositionRoot` builds the services from `JdocEnvironment` and
   starts opening `index.db` on a background thread (the first query waits for it).
2. `module.ClasspathResolver` → `ModuleClasspath` (cache hit: `deps.txt`; miss: Maven). The POM key and reactor come
   from a per-module snapshot while every file the POM analysis touched keeps its stamp.
3. `indexer.ClasspathIndexer` makes sure the JDK and every jar on the classpath is indexed and current (stat, re-index
   only changed jars, under the file lock).
4. The `app` service for the command asks `lookup`, scoped to those artifact ids. Every command except `source`
   answers from SQLite alone; `source` reads the jar entry and cuts the member by its stored offsets, parsing or
   decompiling only when needed.
5. The `CommandResult` (exit code and lines) goes back to `cli`, which paginates with `text` and prints.

Per-command chains, the indexing flow and the install-time AOT training: see the area maps.

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
