# Code map: lookup, app, cli and `Main`

Paths are below `src/main/java/io/github/vecluu/jdoc/` (tests below `src/test/java/...`). Binding rules (layering,
stdout ownership, runtime flow) are in [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md); this file only maps
the code.

## Purpose

The read side and the front door of jdoc. `lookup` answers each question (which type, which members, which docs,
which source) as structured values from the SQLite index, scoped to one module's artifacts. `app` runs one command
end to end (resolve module → index → lookup → format) and returns a `CommandResult`; `cli` parses the command line,
wires the concrete components, paginates and prints. `Main` is the process entry point.

## Key types

**`Main`** — sets slf4j-simple defaults (warn, stderr, no thread/logger name) unless `-Dorg.slf4j.simpleLogger.*` is
given, then runs `cli.JdocCli`, or `cli.TrainingWorkload` when the `jdoc.aotTraining` system property is set.
`Main.run(args, out, err)` is the testable, non-exiting variant.

**`cli`** (only package writing to stdout; package-private except `JdocCli`, `CompositionRoot`, `TrainingWorkload`,
`TrainingLibrary`)
- `JdocCli` — `execute(args)` → exit code: parse, print help/version (exit 0) or run a command; turns
  `UsageException` and any `RuntimeException` into one `error:` line (exit 3). `forEnvironment(...)` = real wiring.
- `CommandLineParser` — hand-written parser → sealed `CliRequest` (`Help`, `Version`, `Run(JdocCommand, Invocation)`).
- `JdocCommand` — enum of the 8 commands: name, argument label, help lines, accepted options, and `call(app,
  invocation)` (a plain `switch` onto `app.Application`). `OptionDefinition` — `FROM`, `PAGE`, `FORCE`.
  `Invocation` — parsed argument, `--from`, `--page`, `--force`.
- `HelpText` — the hand-written `--help` texts (overview and per command). `ManifestVersion` — `--version` from the
  jar manifest (`development build` outside the jar).
- `CommandRunner` — creates the `Application` only when a command runs, calls it, closes it.
- `ResultPrinter` — stderr lines first, then the requested page via `text.Paginator`; page past the end → message on
  stdout, exit 1. `CommandLineText` — shell-quoted command line for the pagination footer.
- `FailureReporter` — the `error:` lines; holds the package's only logger (slf4j loads only on failure).
  `UsageException` — malformed command line, knows which `--help` to point at.
- `CompositionRoot` — `create(JdocEnvironment)` → `Application`; the only place that `new`s concrete components.
- `TrainingWorkload` — AOT training run: executes a workload file (one command per line) through fresh `JdocCli`s
  in one JVM. `TrainingLibrary` (+ package-private `TrainingLibrarySources`) — `main` used by `dist/install.sh` to
  compile a throwaway three-flavour library (sources / javadoc / binary) into a Maven layout for that training.

**`app`**
- `Application` — the 8 calls (`find`, `classView`, `method`, `source`, `impls`, `member`, `packageView`, `index`)
  + `close()`. `CommandApplication` — implementation delegating to one command object each; `onClose` closes the DB.
- `LookupCommand` — `run(argument, from)`; implemented by `FindCommand`, `ClassCommand`, `MethodCommand`,
  `SourceCommand`, `ImplsCommand`, `MemberCommand`, `PackageCommand`. `IndexCommand.run(force, from)` is separate.
- `ModuleContext` — `open(from, IndexOptions)` → `ModuleSession`; default `run(...)` opens, runs, closes and prepends
  the classpath warnings to stderr. `IndexingModuleContext` — the real one: `module.ClasspathResolver` →
  `indexer.ClasspathIndexer` → `LookupScopes.from` → fresh `LookupServices` via `LookupServicesFactory`.
- `ModuleSession` — classpath, `LookupScope`, `LookupServices`, `IndexReport` of one call (closing it closes the
  lookup services, not the DB).
- `LookupScopes` — builds the scope from `indexer.IndexedClasspath`, **JDK first** (indexer lists it last).
- `CommandResult` (exit code, optional `text.TextResult`, stderr lines; `found/notFound/ambiguous/error`,
  `withWarnings`) and `ExitCode` (0 found, 1 not found, 2 ambiguous, 3 error).
- Package-private formatting: `Headers` (type header via `text.ResultHeader`, sentence headers, `[version
  unresolved]` tag), `LookupOutcomes` (not found / ambiguous / invalid results for type and member arguments, incl.
  the "is protected and X cannot be subclassed" hint), `ClassViewFormatter` (`jdoc class` layout), `DocLines`
  (`RenderedDoc` → lines; `verbatim` for code).

**`lookup`** (all results are values; services keep per-call caches and are not thread-safe)
- `LookupServices` — record wiring every service from the index repositories, a `JarEntryReader` and a
  `decompile.DecompiledSourceProvider` (`create(...)`); `close()` closes the lazy javac parser.
- Scope and located values: `LookupScope` (artifacts in classpath order + `ResolutionStatus`; `rank`,
  `versionUnresolved`), `ScopedArtifact` (id + `ArtifactKey`, `label`, `isJdk`), `LocatedType` (index row + its
  artifact), `MemberMatch` (member + declaring type + inherited flag; `reference()` = `a.B#m(int)`).
- `ScopedTypes` — scoped type reads ordered by classpath rank, `firstPerName` (shadowing), `isApiVisible`
  (public/protected with an API-visible enclosing chain, cached).
- `TypeResolver` → sealed `TypeLookup` (`Found` + shadowed duplicates, `NotFound` + suggestions, `Ambiguous`,
  `Invalid`). Exact FQCN/binary candidates first, then package-less (nested-aware) simple names.
- `TypeHierarchy` — `directSupertypes`, `supertypes` (javadoc order: superclass chain, interfaces BFS, `Object` last;
  unknown names kept as `Supertype` without a type), `subtypes` → `Subtype(type, direct)` (transitive, BFS, walks
  through non-API types without listing them).
- `OverrideMatcher` — override/hide test without attribution (name + erased params; a bare type variable in the
  supertype matches anything; fields by name; constructors never).
- `TypeMembers` — `declared` (API members, declaration order), `inherited` (minus overridden/hidden, minus static
  interface methods and constructors, minus protected ones on non-subclassable types), `inheritedForSubclassesOnly`.
- `ClassViewService` → `ClassView` (+ `InheritedMembers`). `MemberLookupService` → sealed `MemberLookup` (`Found`,
  `MemberNotFound` with suggestions and hidden protected matches, `TypeNotResolved`, `Invalid`).
- `DocLoader` — stored docs from `index.DocRepository` (`typeDoc`, `memberDoc` → `MemberDoc(doc, inheritedFrom)`,
  `ownMemberDoc`, `packageDoc` → `PackageDoc`); `DocInheritance` (package-private) merges `{@inheritDoc}` / missing
  docs / missing `@param`/`@return`/`@throws` from overridden methods.
- `SourceLoader` → sealed `MemberSource` (`Available(code, doc, decompiled)`, `Unavailable(reason)`); uses
  `SourceFiles` (+ `ParsedText`; cached entry text and lazy `source.JavaSourceParser`), `ArtifactFiles` (artifact rows
  by id), `JarEntryReader` / `ZipJarEntryReader` (one zip entry as UTF-8).
- `FindService` → `FindResult` / `FindHit` (cap `MAX_HITS` = 200). `MemberSearchService` → `List<MemberMatch>`.
  `PackageService` → sealed `PackageLookup` (`Found(PackageView)`, `NotFound(name, suggestions)`).

## Collaborations

- `cli` → `app` (`Application`, `CommandResult`, `ExitCode`), `text` (`Paginator`, `TextResult`, `PageOutcome`),
  `config` (`JdocEnvironment`), `error`. `CompositionRoot` imports nearly every package: `index` (`IndexDatabase`, the
  `JdbcTypeRepository`-style repositories, `JdbcDocRepository`, `FileIndexLock`), `indexer` (`ClasspathIndexer`, `JarArtifactIndexer`, `DefaultJdkIndexer`,
  `SqliteIndexStore`), `module` (`MavenClasspathResolver`), `decompile` (`DecompiledSourceCache`,
  `VineflowerDecompiler`), `bytecode`, `javadoc`, `source`, `config` (`JdkInfoReader`).
- `app` → `module.ClasspathResolver`, `indexer` (`ClasspathIndexer`, `IndexOptions`, `IndexResult`, `IndexReport`,
  `IndexedClasspath`), `lookup`, `query.NamePattern` (pattern validation in `FindCommand`), `text` (`TextResult`,
  `ColumnLayout`, `ResultHeader`), `model`.
- `lookup` → `index` repositories (`TypeRepository`, `MemberRepository`, `SupertypeRepository`,
  `ArtifactRepository`, `DocRepository`), `query` (`TypeReferenceParser`, `MemberReferenceParser`,
  `OverloadMatcher`, `NamePattern`, `ParameterTypes`), `source` (`JavaSourceParser`, `MemberSourceSlicer`, parsed
  types; only for `jdoc source`), `decompile` (`DecompiledSourceProvider`, `DecompilationException`), `model`, `error`.
- Callers from outside: `dist/install.sh` (via `-Djdoc.aotTraining=<workload>` and `java -cp jdoc.jar
  io.github.vecluu.jdoc.cli.TrainingLibrary`), the launcher `dist/bin/jdoc` (`-jar`, `Main`), and
  `scripts/bench-startup.sh`.

## Flows

Shared prefix for every command: `Main.main` → `Main.run` → `JdocCli.forEnvironment(...).execute(args)` →
`CommandLineParser.parse` → `CliRequest.Run` → `CommandRunner.run` → `CompositionRoot.create` (starts
`IndexDatabase.openInBackground`, builds lazy indexers) → `JdocCommand.call` → `CommandApplication.<command>` →
`ModuleContext.run(from, IndexOptions.lookup(), …)` → `IndexingModuleContext.open`: `ClasspathResolver.resolve` →
`ClasspathIndexer.index` → `LookupScopes.from` → `LookupServicesFactory.create`. After the command: `CommandResult`
(+ classpath warnings) → `ResultPrinter.print` → `Paginator.page` → stdout; `Application.close` closes the DB.

- `--help` / `-h` / `--version` / `-V`: `CliRequest.Help`/`Version` → `HelpText` / `ManifestVersion` → stdout,
  exit 0. No application, no DB. Usage error: `UsageException` → `FailureReporter.usage`, exit 3.
- `find <pattern>`: `FindCommand` validates with `NamePattern.parse` (invalid → exit 3 before resolving) →
  `FindService.find` (types via `TypeRepository.listNames` subsequence prefilter + `NamePattern.match`; members via
  `MemberRepository.searchByName` for lower-case patterns or `listNames` + `findByNames` for camel humps) →
  `N matches for p` + rows; empty → exit 1.
- `class <type>`: `TypeResolver.resolve` (not found / ambiguous / invalid → `LookupOutcomes.typeNotResolved`) →
  `ClassViewService.view` (`TypeMembers.declared`, `TypeHierarchy.directSupertypes`, `DocLoader.typeDoc`,
  `TypeRepository.findNested`, `TypeMembers.inherited`) → `ClassViewFormatter.format`.
- `method <ref>`: `MemberLookupService.lookup` (`TypeResolver` → own members by `OverloadMatcher`, else inherited) →
  per overload `DocLoader.memberDoc` (inheritance over `TypeHierarchy.supertypes`) → `DocLines`; not found →
  `LookupOutcomes.memberNotResolved`.
- `source <ref>`: same lookup → per overload `SourceLoader.source`: SOURCES/JDK → `SourceFiles.text` +
  `storedRangeMatches` + `MemberSourceSlicer` (no javac); else `SourceFiles.parse` (javac). JAVADOC/DECOMPILED →
  `DecompiledSourceProvider.source` (Vineflower, cached on disk) → `SourceFiles.parseText` → slice by shape; javadoc
  types also get `DocLoader.memberDoc` as a `/** */` block. Exit 1 if no overload has source.
- `impls <type>`: `TypeResolver` → `TypeHierarchy.subtypes` (`SupertypeRepository.directSubtypesOf`, level by level)
  → rows, `indirect` marked; none → exit 1.
- `member <name>`: blank → exit 3; `MemberSearchService.search` (`MemberRepository.findByName`, exact case wins,
  library before JDK) → rows; none → exit 1.
- `package <pkg>`: `PackageService.describe` (`TypeRepository.findByPackage`, `DocLoader.packageDoc`; not found →
  `listPackages` suggestions) → header with artifact labels, doc, type rows.
- `index [--force]`: `IndexCommand` uses `IndexOptions.indexCommand(force)` (reports missing sources) and formats
  `IndexReport` (counts, ≤ 10 types per artifact without sources, `failed to index:` lines).
- AOT training: `Main.run` sees `jdoc.aotTraining` → `TrainingWorkload.runFile` → one fresh `JdocCli` (and
  `CompositionRoot`) per workload line; only exit 3 counts as failure.

## Tests

- Unit, `lookup/`: one `*Test` per service (`TypeResolverTest`, `TypeHierarchyTest`, `OverrideMatcherTest`,
  `DocInheritanceTest`, `ClassViewServiceTest`, `MemberLookupServiceTest`, `DocLoaderTest`, `SourceLoaderTest`,
  `SourceFilesTest`, `FindServiceTest`, `MemberSearchServiceTest`, `PackageServiceTest`,
  `InheritedProtectedMembersTest`). They run on a real temp SQLite through `LookupIndex` (hand-built descriptors or
  `SampleSources` parsed into a real sources jar / mini `src.zip`).
- Unit, `app/`: one `*CommandTest` per command + `ClassViewFormatterTest`, `DocLinesTest`, `CommandResultTest`,
  `LookupScopesTest`; helpers `AppIndex` (real temp index of `AppSources`, a mini JDK + shapes library),
  `FakeModuleContext`, `CommandTestSupport`.
- Unit, `cli/`: `CommandLineParserTest`, `HelpTextTest`, `CommandLineTextTest`, `JdocCliTest` (with
  `FakeApplication`), `TrainingWorkloadTest`, `TrainingLibraryTest`, `TrainingLibrarySourcesTest`; `MainTest` (root
  package; calls `Main.run`, never `main`).
- `lookup/LookupIT` — every service end to end on `testsupport.SampleLibrary` indexed by the real `JarArtifactIndexer`
  (`SampleLibraryIndexer`): sources, javadoc-only and decompiled types, real Vineflower (~2 s).
- `lookup/JdkFindIT` — indexes the real JDK `src.zip`; camel-hump member find (`oET`, `gOD`) and a warm-time bound.
- `cli/EndToEndIT` (~15 s, indexes the real JDK `src.zip`) — every command and exit code through `JdocCli` with a fake
  `mvn` script and `FakeMavenRepo`: Duration ambiguity, JDK-first shadowing, paging footer, `--from`, jar refresh,
  STALE and UNRESOLVED fallbacks, `index --force`, the fat jar (`builtFatJarRuns`), two concurrent first runs.
- `cli/HotPathClassLoadingIT` (slow, child JVMs on the fat jar with `-Xlog:class+load`) — lookups (and `source` of
  members with source text) load no javac, jsoup, ASM or Vineflower; control: `source` of a decompiled type does.
- `cli/TrainingLibraryIT` — the generator writes the three flavours. `cli/InstallerIT` and `cli/BenchStartupScriptIT`
  live in this package but test `dist/` and `scripts/` (area E); both are slow.
- Fat-jar ITs skip when `target/.../jdoc.jar` is missing; prerequisites go through `testsupport.Prerequisites`, which
  fails instead of skipping when `CI=true`.

## Extension points

- **New command:** add a constant to `cli.JdocCommand` (name, argument, help, options, `call` branch), a method on
  `app.Application` + `CommandApplication`, a `LookupCommand` in `app`, and if needed a service in
  `lookup.LookupServices.create`. `HelpText` picks it up from the enum; update `dist/skill` (area E) and `EndToEndIT`.
- **New option:** a constant in `cli.OptionDefinition`, add it to `JdocCommand.options()`, read it in
  `CommandLineParser.invocation` into `Invocation`.
- **Output format:** per-command formatting lives in the `app` command classes plus `Headers`, `LookupOutcomes`,
  `ClassViewFormatter`, `DocLines`; column alignment, header line and pagination are in `text` (area A).
- **Component swaps:** everything is wired in `cli.CompositionRoot`; boundaries for fakes are `app.ModuleContext`,
  `app.LookupServicesFactory`, `lookup.JarEntryReader`, `decompile.DecompiledSourceProvider`, the `index`
  repository interfaces.
- **Doc-source switches:** `SourceLoader.source` and `SourceLoader.sourceContainer` switch on `model.DocSource`; a new
  doc source must be handled there and in `DocLoader` (DECOMPILED never has docs).
- **Ranking:** `FindService.find` sort (score → library before JDK → types before members → reference) and
  `MemberSearchService` (library before JDK).

## Gotchas

- **JDK first in the scope.** `LookupScopes.from` reorders the indexer's list so a library declaring
  `java.util.Optional` never shadows the JDK's (javac semantics). Same binary name in several artifacts is not
  ambiguous: the first in classpath order wins (`TypeLookup.Found.shadowed`).
- **Hot path must stay free of javac/jsoup/ASM/Vineflower** (enforced by `HotPathClassLoadingIT`). Deliberate tricks:
  `SourceFiles(JarEntryReader)` uses an anonymous `Supplier` and `CompositionRoot.lazyVineflower` a lambda instead
  of constructor references (a method reference or a lambda returning the concrete type loads that class eagerly);
  indexers are memoized suppliers; `FailureReporter` is the only `cli` class with a logger so slf4j loads only on
  failure. `JdocCommand` is a plain enum with a `switch` (no lambdas/reflection) for the same reason (AOT cache).
- **Docs come from the index, not from source**. Inheritance is resolved at lookup time in `DocLoader` because
  supertypes may live in other artifacts; only non-static methods inherit. `{@inheritDoc}` text is prepended (the tag
  position is lost after rendering).
- **`source` offsets:** stored member ranges are trusted only if `SourceLoader.storedRangeMatches` (range inside the
  text, doc range starts with `/**`, the member name appears as a whole word); otherwise the entry is re-parsed. For
  the JDK, `IndexedArtifact.jarPath()` is `src.zip`; a JDK indexed without `src.zip` gives `Unavailable("the JDK has
  no src.zip")`.
- **Protected inherited members** are hidden on final, sealed, enum and record types (`TypeMembers.canBeSubclassed`
  reads the `modifiers` text; `non-sealed` stays subclassable); `jdoc method` on one exits 1 with an explanatory line.
- **`find` truncation** is flagged both when hits exceed 200 and when the member row scan hits `MEMBER_SCAN_LIMIT`
  (5,000). The index prefilters use ASCII-only `LIKE`, so non-ASCII pattern characters are dropped from the SQL side.
  A pattern with a `.` matches type display names and skips members; camel humps apply only if the pattern has an
  upper-case letter.
- **Output streams:** not-found and page-out-of-range text go to stdout (exit 1); only exit-3 errors and classpath
  warnings go to stderr. `CommandResult.error` has no stdout. Usage errors end with ` (see jdoc <cmd> --help)`.
- **Parser wording** mimics picocli's messages (`Unmatched argument at index …`, `Missing required parameter: …`);
  tests assert them. A lone `-` is an argument, `--` ends options, options repeat → error.
- **`[version unresolved]`** is added only to library type headers and sentence headers when the scope is
  UNRESOLVED; JDK headers never get it (`Headers`, `PackageCommand.header`).
- **Per-call objects:** `LookupServices` and their caches are created per call (`LookupServicesFactory`); the DB is
  closed by `Application.close` (via `CommandApplication`'s `onClose`), not by `ModuleSession`.
- **Training workload lines** are split on whitespace (no quoting), and each line builds a new `CompositionRoot`.
