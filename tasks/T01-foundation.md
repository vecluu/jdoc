# T01 — Foundation: config, model, errors, test fixtures

**Packages owned:** `config`, `model`, `error`, the shared text-rules class in `javadoc` (see §5), `testsupport`
(test sources only), `Main` (placeholder stays).
**Depends on:** nothing. Every other task builds on what you create, so precision matters more than speed.
**Spec:** `docs/ARCHITECTURE.md` sections "Configuration", "Domain model", "Cross-cutting rules";
`OUTLINE.md` "Index and cache", "Doc sources and fallback".

## Goal

Create the shared vocabulary of the program and the test infrastructure every other task uses.

## Scope

### 1. `config`
- `JdocEnvironment`: immutable; built from an env-var map and system properties (factory for the real process, and a
  builder/factory for tests). Accessors exactly as in the architecture table (cache dir, local repo, Java home,
  Maven executable, working directory). Resolve `~`-free absolute, normalized paths.
- `CachePaths`: every path under the cache directory as listed in the architecture doc, including the per-module
  directory for a POM key and the decompiled directory for a `Gav`. Directory creation on demand, not in the
  constructor.
- `JdkInfo` (or similar): reads the Java home's `release` file to obtain the feature version (`25`) and locates
  `lib/src.zip`. Missing `src.zip` is reported as an absent `Optional`, not an exception.

### 2. `model`
Implement every value in the architecture "Domain model" table with the given names and contents. Pay attention to:
- `TypeName` derivations for nested types (`a.b.Outer$Inner$Deeper`), default package, and names containing `$`
  only as nesting separators. Provide factories from binary name, from a source-style display name with known
  nesting depth, and from package + list of nested simple names.
- `Gav` parsing/formatting helpers that downstream tasks need (`g:a:v`, `g:a:type:classifier:v`); repository-relative
  path of the jar and of its `-sources.jar` / `-javadoc.jar` siblings.
- `ParameterInfo` and `MemberDescriptor` must make overload matching possible without re-parsing source.
- Validation in compact constructors (non-null, non-blank where required).

### 3. `error`
`JdocException` (unchecked) with focused subclasses such as resolution failure, index/storage failure, jar read
failure, parse failure. Keep it small; downstream tasks may add subclasses in their own packages.

### 4. Test fixtures (`src/test/java/io/github/vecluu/jdoc/testsupport`)
Reusable, documented builders used by all later tests:
- **`JarFixture` / `TestLibrary` builder:** takes in-memory Java sources (file name → text), compiles them with the
  JDK's `javax.tools` compiler (with `-parameters` optional), and writes into a temp dir any of: binary jar, sources
  jar, javadoc jar. Generate the javadoc jar by running the JDK's own `javadoc` tool through `ToolProvider`
  (`javax.tools.ToolProvider.getSystemDocumentationTool()`), so tests get the real JDK 25 HTML layout.
  Options to omit chosen `.java` files from the sources jar (to simulate per-type fallback) and to add `.kt` files.
- **`FakeMavenRepo`:** lays out `groupId/artifactId/version/` directories with the jars above under a temp
  `repository` root, and returns `ResolvedDependency` values pointing at them.
- **`TestEnvironments`:** builds a `JdocEnvironment` whose cache, repo, and Maven executable point into a temp dir.
- A tiny sample library (a few classes: an interface, an implementation, an enum, a record, a nested class, a
  generic method with overloads, rich Javadoc using `{@code}`, `{@link}`, `<p>`, `<pre>`, `@param`, `@return`,
  `@throws`, `@since`, `@deprecated`, `{@inheritDoc}`) available as a ready-made fixture, because T04, T05, T08,
  T09 and T10 will all test against it.

### 5. Shared Javadoc text rules (`javadoc` package, one class plus helpers)
T04 (doc comments via `DocTrees`) and T05 (javadoc-jar HTML) run in parallel and must render identically, so the
shared rules live here: a formatter that turns an HTML fragment (as found in Javadoc comments and javadoc pages)
into compact plain text per `OUTLINE.md` "Javadoc rendering": `<code>`/`<tt>` → backticks, `<a>` → its text,
`<p>` → blank line, `<pre>` kept verbatim, lists → `- item` lines, other tags stripped, entities decoded,
whitespace collapsed outside `<pre>`. Use jsoup. Also a helper that formats block-tag lines (`@param name text`,
`@return text`, `@throws Type text`, `@since text`, `@deprecated text`) as single lines, and a first-sentence
extractor (javadoc's rule: up to the first period followed by whitespace, ignoring common abbreviations like
`e.g.` and `i.e.`). Thoroughly unit-tested.

## Tests
- Unit tests for every model value with logic (`TypeName`, `Gav`, `CachePaths`, `JdocEnvironment`, `JdkInfo`).
- Integration test `TestLibraryIT` proving the fixture builds all three jar kinds and the javadoc jar contains the
  expected HTML pages.

## Done when
- All of the above exists, `verify` passes, and `tasks/notes/T01.md` lists every public type and fixture with a
  one-line description so wave-2 agents can work from the notes alone.
