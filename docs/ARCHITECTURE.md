# jdoc architecture

The rules the code follows and the paths that cross packages. Each package says what it is for in its
`package-info.java`. What the program does and why is in `docs/SPEC.md`; this file does not repeat it.

## Repository layout (besides the Java sources)

```
pom.xml, mvnw, .mvn/         single-module Maven build (always ./mvnw); shaded, reproducible target/jdoc.jar
dist/                        bin/jdoc (POSIX launcher), install.sh, training/fake-mvn (fake `mvn` for AOT training and the tests), skill/SKILL.md, AGENTS-snippet.md
scripts/                     assemble.sh, smoke-test.sh, install-test.sh, bench-startup.sh, release-notes.sh
it/sample-project/           small Maven project for the smoke and install tests
.github/workflows/build.yml  CI and release
```

## Rules

- **Layering.** Dependencies point down a fixed layering; `PackageDependencyTest` holds the allowed edges between
  packages. Only `cli.CompositionRoot` may import everything; it is also the only class wiring concrete classes.
- **Object-oriented design.** Single responsibility, small classes, interfaces at component boundaries (anything that
  touches the filesystem, a process, the database or a jar), constructor injection, composition over inheritance.
  No service locators, static mutable state or singletons.
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
  or Vineflower; indexing components are created lazily. `HotPathClassLoadingIT` enforces it.
- **Index format.** Anything that changes what is stored bumps `index.Schema.VERSION` (old indexes are dropped and
  rebuilt); anything that changes the POM analysis bumps `module.ModuleSnapshotCodec.FORMAT_VERSION`.
- **Javadoc** on every package (`package-info.java`) and public type (`JavadocPresenceTest`; javac doclint checks the
  references) and on non-obvious public methods. No commented-out code, no stray TODOs.
- **Docs name real things.** Type names and paths in backticks here, and paths in the other docs, must exist
  (`DocReferencesIT`). The shaded jar's licence files cover every bundled dependency (`LicenseNoticesIT`).
- Linux only, UTF-8 everywhere. Base package `io.github.vecluu.jdoc`, main class `io.github.vecluu.jdoc.Main`.

## Tests

- **Unit tests** `*Test` (surefire): every non-trivial class, normal, edge and error cases, well under a second each.
  **Integration tests** `*IT` (failsafe): real components, files, jars or SQLite; whole-JDK `src.zip` indexing only
  here. ITs that index the real JDK, run real Maven or spawn installs and JVMs carry `@Tag("slow")`. No test class
  name starts with `Test` (surefire would run a `Test...IT` as a unit test).
- JUnit Jupiter and AssertJ; no Mockito, but small hand-written fakes of the boundary interfaces. Shared fixtures come
  from `testsupport`. No test touches the real `~/.cache/jdoc` or `~/.m2` (use `@TempDir`) or the network.
- A test that needs an external tool or file calls `testsupport.Prerequisites` (skips locally, fails in CI), never a
  bare JUnit assumption.

## Dependencies

Fixed: `pom.xml` changes and new dependencies go through the orchestrator. A new runtime dependency also needs a
`THIRD-PARTY-NOTICES` section (and `NOTICE` if its licence asks), `LicenseNoticesIT.OWNERS` entries and a row here,
and stays off the hot path unless lookups need it (reasoning: `docs/decisions/licensing.md`).

| Dependency | Constraint |
| --- | --- |
| `jdk.compiler` (JDK) | parse only (`JavacTask.parse()`), `DocTrees` for Javadoc; no attribution |
| `java.xml` (JDK) | POM files as DOM; no Maven model library |
| sqlite-jdbc | the index; bundles its native libraries |
| jsoup, ASM, Vineflower | indexing and `source` only, never on the hot path; Vineflower is the last resort |
| slf4j-api + slf4j-simple | stderr only |
| Lombok (provided), JUnit Jupiter, AssertJ (test) | no Mockito |

## Request path and startup

1. `Main` → `cli.JdocCli` parses the arguments; `cli.CommandRunner` asks `cli.CompositionRoot` for the
   `app.Application`, which starts opening `index.db` on a background thread. `--help` and `--version` stop here.
2. The `app` command → `app.IndexingModuleContext`: `module.ClasspathResolver` gives the `ModuleClasspath` (cached
   `deps.txt`, else Maven); `indexer.ClasspathIndexer` indexes what is new or changed, under the file lock.
3. `app.LookupScopes` → fresh `lookup.LookupServices`, scoped to those artifacts; every command except `source`
   answers from SQLite alone.
4. The `app.CommandResult` goes to `cli.ResultPrinter`, which paginates (`text.Paginator`) and prints.

Startup (why: `docs/decisions/startup.md`): `dist/install.sh` trains an AOT cache with `cli.TrainingWorkload` →
`dist/bin/jdoc` uses it while its fingerprint matches, else a per-user CDS archive → `java -jar jdoc.jar` → `Main`.

## Change checklists: what else to touch

- **New command:** the training `workload` in `dist/install.sh`, a row in `HotPathClassLoadingIT` (lookups), a case in
  `EndToEndIT`; `docs/SPEC.md`, `README.md`, `dist/skill/SKILL.md`, `dist/AGENTS-snippet.md`.
- **New option:** `cli.OptionDefinition` → `cli.JdocCommand` → `cli.CommandLineParser`; SPEC and README.
- **Stored data or signature change:** bump `index.Schema.VERSION`. Signatures are rendered twice, in
  `source.SignatureFormat` and `bytecode.MemberDescriptorFactory`: change both.
- **New doc source or fallback order:** `model.DocSource`, the indexer's fallback, and the `DocSource` switches in
  `lookup.SourceLoader` and `lookup.DocLoader`; SPEC "Doc sources".
- **Bundle contents:** `scripts/assemble.sh`, `scripts/install-test.sh` and `cli.InstallerIT` together.
