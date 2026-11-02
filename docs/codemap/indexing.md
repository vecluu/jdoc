# Code map: indexing (`source`, `javadoc`, `bytecode`, `decompile`, `indexer`)

Paths are relative to `src/main/java/io/github/vecluu/jdoc/` (tests: `src/test/java/io/github/vecluu/jdoc/`) unless they start
with `src/`. Binding rules, runtime flows, change recipes and the test map are in
[`docs/ARCHITECTURE.md`](../ARCHITECTURE.md); behaviour is in [`docs/SPEC.md`](../SPEC.md) (sections "Index, cache and refresh", "Doc sources",
"Signature format"). Neither is repeated here. The database layout is in [`resolution-and-index.md`](resolution-and-index.md).

## Purpose

The write side of jdoc. It turns a binary jar (plus its `-sources.jar` / `-javadoc.jar`) or the JDK into
`TypeDescriptor`/`MemberDescriptor` rows with fully rendered docs (`RenderedDoc`) and hands them to the index. The doc
source is chosen per type: sources, then javadoc HTML, then bytecode only. `decompile` is the one piece used at
lookup time instead: it produces source text for `jdoc source` on types that have no sources.

## Key types

**`indexer`** (orchestration, the only package here that writes to the index)
- `indexer.ClasspathIndexer`: entry point, `index(ModuleClasspath, IndexOptions) → IndexResult`. It stats the jars,
  decides what is stale, takes the `index.IndexLock` only when something is pending, skips remembered failures, and
  builds the `IndexedClasspath` (the artifact ids every lookup is scoped to, classpath order, JDK last).
- `indexer.IndexOptions` (`lookup()`, `indexCommand(force)`), `indexer.IndexResult`, `indexer.IndexReport`,
  `indexer.IndexFailure`, `indexer.ArtifactWithoutSources`, `indexer.TypeWithoutSources`: inputs and results.
- `indexer.ArtifactIndexer` → `indexer.JarArtifactIndexer`: one Maven jar. Scans the jar, groups classes by top-level
  type, runs the fallback per group, writes types in batches, then one package doc per package.
- `indexer.TypeDocResolver` (package-private): the per-type fallback SOURCES → JAVADOC → DECOMPILED, plus
  `packageDoc(pkg)` (`package-info.java`, else `package-summary.html`).
- `indexer.ParameterNames` (package-private): puts real parameter names from a javadoc page into a bytecode member
  and rewrites the stored signature.
- `indexer.JdkIndexer` → `indexer.DefaultJdkIndexer`: the JDK. It uses `indexer.JdkSourceZipProducer` (parallel javac
  over `lib/src.zip`) or, without `src.zip`, `indexer.JrtBytecodeProducer` (`jrt:/`, bytecode only, DECOMPILED).
  `indexer.JdkPackageFilter` keeps `java.*`/`javax.*`/`jdk.*`, no `internal`, unqualified exports only.
- `indexer.IndexStore` → `indexer.SqliteIndexStore`: `replace(artifact, EntryProducer)` in one transaction over the
  `index` repositories, plus `findFailure`/`recordFailure`/`typesWithoutSources`. `indexer.EntryProducer` /
  `indexer.EntrySink` stream `indexer.TypeEntry` (type + doc + `indexer.MemberEntry` list) batches and package docs.
- Filesystem boundaries (interface → real impl): `indexer.FileStatter` → `indexer.NioFileStatter` (`indexer.FileStamp` =
  mtime + size); `indexer.DocJarLocator` → `indexer.SiblingDocJarLocator` (`indexer.DocJars`);
  `indexer.ArchiveOpener` → `indexer.ZipArchiveOpener` (`indexer.SourceArchive`, `indexer.JavadocArchive`);
  `indexer.DecompiledCacheEvictor` → `indexer.DecompiledDirectoryEvictor`.
- `indexer.SourceFiles` (package-private): reads and parses one `.java` entry. A javac failure is logged and the file
  counts as absent. `indexer.StoredArtifact`: the saved artifact plus type and member counts.

**`source`** (javac Tree API, parse only, no attribution)
- `source.JavaSourceParser`: `parse(JavaSource, KnownTypes) → ParsedSourceFile`. It reuses one compiler and file
  manager, so it is not thread-safe: use one per thread and close it. Syntax errors go to `problems()`, they do not throw.
- `source.JavaSource` (path, text, doc source; never JAVADOC), `source.KnownTypes` (which binary names exist),
  `source.ParsedSourceFile` / `source.ParsedType` / `source.ParsedMember` (descriptor + full `RenderedDoc`,
  `implicit` flag).
- `source.CompilationUnitExtractor`: walks one unit and builds visibility, modifiers, supertypes, signatures,
  ranges and docs. Helpers: `source.TypeNameResolver` (best-effort name resolution, order documented in its
  Javadoc), `source.TypeRenderer`, `source.TypeScope`, `source.SignatureFormat`, `source.SourceRanges`,
  `source.ImplicitMembers` (default ctor, enum `values`/`valueOf`, record ctor/accessors/`toString`/`hashCode`/`equals`),
  `source.JavaLangTypes` (hard-coded JDK 25 `java.lang` list).
- `source.MemberSourceSlicer`: cuts a member, doc comment or declaration out of text by `SourceRange`. Used by
  `lookup.SourceLoader`, not by the indexer.

**`javadoc`** (Javadoc to `RenderedDoc`; two front ends share the same text rules)
- Shared rules: `javadoc.HtmlTextFormatter` (HTML fragment to text, jsoup), `javadoc.FirstSentenceExtractor`,
  `javadoc.BlockTagFormatter`, internal `javadoc.TextBuilder` and `javadoc.PreBlock`.
- Doc comments (from `source`): `javadoc.DocCommentRenderer` (`DocCommentTree` → `RenderedDoc`) with
  `javadoc.DocCommentHtml` (inline tags → HTML).
- Javadoc jars (from `indexer`): `javadoc.JavadocJar` (zip + charset) implements `javadoc.JavadocPageSource`;
  `javadoc.JavadocJarReader` (`typePage`, `packageDoc`; drops an `@since` copied from the outer type);
  `javadoc.JavadocPageParser` (jsoup, JDK 17+ layout first, JDK 8–16 where anchors are found) →
  `javadoc.JavadocTypePage` (`memberDoc`, `parameterNames`) of `javadoc.JavadocMemberDoc` sections;
  `javadoc.JavadocAnchor` (modern/legacy anchor ids, `matches` / `matchesLoosely`), `javadoc.JavadocSectionRenderer`,
  `javadoc.JavadocLinkRewriter` (`Foo.bar(int)` → `Foo#bar(int)`), `javadoc.SignatureParameterParser` +
  `javadoc.JavadocParameter` (real names from the member signature text, all layouts).

**`bytecode`** (ASM, `SKIP_CODE | SKIP_FRAMES`)
- `bytecode.BinaryJarScanner` → `bytecode.ZipBinaryJarScanner`: streams `bytecode.ScannedClass` (type + public/protected
  members) per class entry and returns a `bytecode.JarScanSummary`. Skips `META-INF/**`, `module-info`, `package-info`.
- `bytecode.ClassFileReader`: one class file → `ScannedClass`. Empty for synthetic, anonymous, local classes and
  anything nested in them; throws `IllegalArgumentException` for invalid bytes. Also used directly by the jrt fallback.
- Internals: `bytecode.ClassCollector` (ASM visitor → `bytecode.RawClass`), `bytecode.TypeDescriptorFactory`,
  `bytecode.MemberDescriptorFactory`, `bytecode.SignatureParser` + `bytecode.JvmType` (descriptor/generic AST),
  `bytecode.TypeRenderer` (simple names, `Outer.Inner`), `bytecode.AccessFlags`.

**`decompile`** (Vineflower, in-process)
- `decompile.Decompiler` → `decompile.VineflowerDecompiler`: `decompile(jar, type)` returns the source of the
  *top-level* type. It feeds only `Outer.class` + `Outer$*.class`; the jar is a lazy library. Options are
  deterministic: one thread, no banner or comments, `\n`, 4-space indent.
- `decompile.DecompiledSourceProvider` → `decompile.DecompiledSourceCache`: reads or writes
  `decompiled/<g>/<a>/<v>/<pkg>/<Outer>.java` (temp file + atomic move). Never used for JDK types.
- `decompile.DecompilationException` (message `Cannot decompile <name>: ...`); internals `decompile.SelectedClassesSource`,
  `decompile.JarLibrarySource`, `decompile.Slf4jFernflowerLogger` (debug logging; detects whole-class failures).

## Collaborations

- **Called by:** `app`. Every command calls `ModuleContext.run(from, IndexOptions.lookup(), ...)`, or
  `indexCommand(force)` for `jdoc index`. `app.IndexingModuleContext` then calls `ClasspathIndexer.index`, and
  `app.IndexCommand` formats the `IndexReport`.
  `lookup.SourceLoader` / `lookup.SourceFiles` use `JavaSourceParser`, `MemberSourceSlicer` and
  `DecompiledSourceProvider` (only for `jdoc source`). `cli.CompositionRoot` wires everything.
- **Calls:** `index` (`ArtifactRepository`, `TypeRepository`, `MemberRepository`, `DocRepository`,
  `FailedArtifactRepository`, `IndexDatabase.inTransaction`, `IndexLock`), `config` (`JdkInfo`, `CachePaths`), `model`,
  `error` (`JarReadException`, `SourceParseException`, `IndexException`, `CacheIoException`, all `JdocException`).
- Inside the area: `indexer` → `source`, `javadoc`, `bytecode` (never `decompile`: it only evicts the cache directory);
  `source` → `javadoc` (doc comments). `javadoc`, `bytecode` and `decompile` depend on nothing in the area.
- Lazy wiring (`cli.CompositionRoot`): the jar and JDK indexers are memoized suppliers behind lambdas, and Vineflower
  is behind a lambda too. Lookups that index nothing therefore load no javac, jsoup, ASM or Vineflower classes.

## Flows

**Lookup call, nothing changed:** `ClasspathIndexer.index` → per dependency `FileStatter.stat` + `IndexStore.find`.
Path, mtime and size all match, so the artifact is reused. The JDK target is checked the same way against
`JdkIndexer.indexedFile` (`src.zip`, else `lib/modules`). No lock is taken; the result is `IndexedClasspath`.

**Indexing a jar:** `ClasspathIndexer` (stale, or `force`) → `IndexLock.acquire` → re-check `find` (another process
may have indexed it) → re-check the remembered failure → `DecompiledCacheEvictor.evict(gav)` if it was indexed before →
`JarArtifactIndexer.index`:
1. `ZipBinaryJarScanner.scan` → `ClassFileReader.read` per class → groups by `TypeName.topLevel()`; all binary names
   form the `KnownTypes`.
2. `SiblingDocJarLocator.locate` → `<stem>-sources.jar` / `<stem>-javadoc.jar` next to the jar.
3. `SqliteIndexStore.replace` (one transaction: save artifact, forget failure) runs the producer:
   for each top-level group `TypeDocResolver.resolve`:
   - SOURCES: `SourceFiles.parse(Outer.java)` → `JavaSourceParser` → `ParsedType` found → source descriptor, members,
     docs; **supertypes overwritten from bytecode**;
   - JAVADOC: `JavadocJarReader.typePage` → `JavadocPageParser.parseTypePage` → bytecode descriptors + page docs +
     `JavadocTypePage.parameterNames` → `ParameterNames.apply`; entry path = `TypeName.javadocPagePath()`;
   - DECOMPILED: bytecode descriptors, no docs; entry path = class entry.
   Batches go to `EntrySink.types` (types, members, type docs, member docs), then `EntrySink.packageDoc` per package.
4. On a `JdocException`: warn, `IndexFailure`, `recordFailure` (path + mtime + size), older index kept in scope.

**Indexing the JDK:** `ClasspathIndexer` → `DefaultJdkIndexer.index` → `JdkSourceZipProducer.produce`. It lists every
`.java` (all form `KnownTypes`, so supertypes resolve exactly), reads exports from each `module-info.java` (regex),
filters packages, and parses on `defaultThreads()` workers (one per core, capped), one `JavaSourceParser` each, behind a
bounded queue. The calling thread is the only DB writer (in batches, plus `package-info.java` docs). Entry paths
keep the module: `java.base/java/util/Optional.java`. Without `src.zip`, `JrtBytecodeProducer` is used: module
descriptors give the exports, no docs, one warn.

**`jdoc source` (read side, for reference):** `lookup.SourceLoader` slices SOURCES/JDK members by stored offsets
(`MemberSourceSlicer`). It re-parses only if the offsets are stale. JAVADOC/DECOMPILED types go through
`DecompiledSourceCache.source` → `VineflowerDecompiler` → parse as `DocSource.DECOMPILED` → slice.

## Tests

Under `src/test/java/io/github/vecluu/jdoc/<package>/`. Unit tests use fakes: `indexer.InMemoryIndexStore`,
`indexer.MapSourceArchive`, `indexer.MapJavadocArchive`, `indexer.Scanned`, `bytecode.InMemoryCompiler`,
`javadoc.DocCommentFixture`, `source.ParsedSources`. Real jars come from `testsupport.SampleLibrary` / `TestLibrary`
(real javac + JDK 25 javadoc into a `@TempDir`).

- `source.JavaSourceParserTest`, `javadoc.DocCommentRendererTest`, `javadoc.JavadocPageParserTest`,
  `bytecode.ClassFileReaderTest`, `indexer.ClasspathIndexerTest` (refresh, lock, failures), `indexer.TypeDocResolverTest`
  (fallback, parameter names): the main unit suites.
- `bytecode.SampleLibraryBytecodeIT`: bytecode signatures equal source-parsed ones member by member (also interface
  modifiers). It is the guard for "source and bytecode render identically".
- `javadoc.JavadocJarIT`: real javadoc jar; docs equal source rendering for explicit comments; real parameter names.
- `source.SampleLibraryParseIT`: the whole fixture sources jar, ranges slice their members.
- `indexer.ArtifactIndexerIT`: mixed library into real SQLite, right doc source per type, exact supertypes across
  artifacts, only API members, re-index replaces.
- `indexer.ClasspathIndexerIT`: whole flow with the real lock and a miniature `src.zip`: reuse, touch → re-index,
  force, concurrent indexers, missing/corrupt/truncated jars, remembered failures.
- `indexer.Jdk8JavadocParameterNamesIT`: commons-lang3 3.5 legacy javadoc (jars copied by the build to
  `jdoc.itFixtureJars`; fails instead of skipping when `CI=true`).
- `decompile.VineflowerDecompilerIT`: decompiles fixture types, output parses, deterministic, cache hit.
- **Slow** (read the running JDK): `indexer.JdkIndexerIT` (full `src.zip` + jrt fallback),
  `source.JdkSourceParseIT` (all of java.base), `bytecode.JdkJmodSmokeIT` (all of java.base via `jrt:/`).
- The hot-path guard `cli.HotPathClassLoadingIT` is described in [`lookup-and-cli.md`](lookup-and-cli.md).

## Extension points

- **New doc source tier or a change to the fallback order:** `indexer.TypeDocResolver` (plus `model.DocSource` and
  the header tags, [`foundation.md`](foundation.md)). A per-package doc source goes in `TypeDocResolver.packageDoc`.
- **New input container** (e.g. a directory instead of a jar): implement `indexer.EntryProducer` and call
  `IndexStore.replace`, like `JdkSourceZipProducer` and `JrtBytecodeProducer` do. For other doc-jar locations, implement
  `indexer.DocJarLocator`. For other archive formats, implement `indexer.ArchiveOpener`.
- **Javadoc HTML layouts:** `JavadocPageParser.modernSections` / `legacySections`, `javadoc.JavadocAnchor.Layout`, and
  `SignatureParameterParser` for signature markup.
- **New inline or block tag:** `javadoc.DocCommentHtml` / `DocCommentRenderer.blockTag` on the source side, and
  `JavadocSectionRenderer.notes` on the HTML side. Both must produce the same text (`JavadocJarIT` checks this).
- **Decompiler swap:** implement `decompile.Decompiler`; the cache and lookup only see `DecompiledSourceProvider`.
- **Stored output changes need a schema bump:** any change to signatures, docs, parameter names or entry paths written
  at index time must bump `index.Schema.VERSION`. Otherwise existing caches keep the old text: they are only
  rebuilt on a version mismatch.

## Gotchas

- **Source and bytecode must render identically** (signature format in `docs/SPEC.md`). A rule changed in
  `source.SignatureFormat` / `CompilationUnitExtractor` must be mirrored in `bytecode.MemberDescriptorFactory` /
  `TypeDescriptorFactory`, or JAVADOC/DECOMPILED types will differ from SOURCES types. Interfaces never show
  `abstract` or `static final`. `non-sealed` cannot be seen in bytecode.
- There are two package-private classes named `TypeRenderer` (`source` and `bytecode`); edit the right one.
- Supertypes of jar types always come from bytecode, even for SOURCES types. JDK supertypes come from source, where
  they are exact thanks to the src.zip-wide `KnownTypes`. Source name resolution elsewhere is a guess
  (`TypeNameResolver`).
- Types of every visibility are stored (package-private and private nested ones too); members only if
  `visibility().isApi()`. The scanner already drops non-API members; `TypeDocResolver.apiMembers` filters parsed ones.
- The SOURCES lookup is per top-level file: nested types follow `Outer.java`. A type missing from the parsed file
  (generated code) falls through on its own. `.kt`/`.scala` files never count. The JAVADOC tier needs the type's own page.
- `ScannedClass.type()` always comes back as DECOMPILED with a class entry path; the indexer overrides it via
  `toBuilder()`. Forgetting this marks a type as decompiled.
- `TypeEntry` member docs are aligned by position with the members (`DocRepository.insertMemberDocs`). Keep the lists
  parallel. `RenderedDoc.missing()` is never stored (no row = no doc).
- `JavaSourceParser` is not thread-safe. The JDK producer creates one per worker, the jar indexer one per artifact.
  `index.IndexDatabase` / `SqliteIndexStore` are single-threaded: only the calling thread writes.
- JDK type insertion order (and so type ids) follows worker completion order, so it differs between runs. Do not rely
  on type id order for the JDK.
- The JDK artifact key is `jdk:<feature>` and its stamp is `src.zip`'s mtime and size. Switching between two JDKs of
  the same feature version (or a patch update) re-indexes the JDK, and alternating `JAVA_HOME` keeps re-indexing it.
- `JdkIndexer` is used as a lambda in `cli.CompositionRoot`, so `indexedFile` is always the interface default.
  Overriding it in `DefaultJdkIndexer` would have no effect there.
- `force` re-indexes Maven artifacts only; the JDK needs `forceJdk` (no CLI flag sets it at present).
- Failures are remembered per artifact key with the jar's path, mtime and size; a changed jar or `force` retries.
  An unchanged failing jar is skipped *before* the lock and logs one warn per call.
- The decompiled cache is not checked against the jar; it is evicted only when an already-indexed Maven artifact is
  re-indexed (`DecompiledDirectoryEvictor`). The eviction happens before indexing, even if indexing then fails.
- `source` → `javadoc.HtmlTextFormatter` uses jsoup, so parsing source loads jsoup as well as javac. Keep both off
  the lookup path (`HotPathClassLoadingIT`).
- Vineflower plugins (Kotlin, idea-not-null) ship in the shaded jar and change output. The decompile tests run on the
  unshaded jar, so they do not see this.
- `JdkSourceZipProducer` skips file names containing `-` (other than `package-info.java`), and a file that throws
  while parsing is logged and skipped. A worker `Error` aborts the whole JDK index.
