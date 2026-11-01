# jdoc architecture and shared contracts

This document is the contract between all implementing agents. `OUTLINE.md` is the product spec; this file decides
how the Java program is structured. Only the Java CLI is in scope (no CI pipeline, scripts, installer or skill files).

## Build and toolchain

- Java 25, Maven via the wrapper: always run `./mvnw` (there is no system `mvn`).
- Base package: `io.github.vecluu.jdoc`. Main class: `io.github.vecluu.jdoc.Main`.
- `./mvnw verify` builds `target/jdoc.jar` (shaded fat jar) and runs unit tests (surefire, `*Test`) and integration
  tests (failsafe, `*IT`).
- Parallel agents must use their own build dir: `./mvnw -B -Djdoc.buildDir=target/<TASK-ID> verify`
  (or `test -Dtest=...` for a quick loop).
- `pom.xml` is owned by the orchestrator. Do not add dependencies. If you truly need one, stop and report it.

### Dependencies (fixed)

| Dependency | Use |
| --- | --- |
| `jdk.compiler` (JDK) | javac Tree API: parse only (`JavacTask.parse()`), `DocTrees` for Javadoc. No attribution. |
| `java.xml` (JDK) | Reading POM files (DOM). No Maven model library. |
| sqlite-jdbc | Index database |
| jsoup | Javadoc-jar HTML |
| ASM (`org.ow2.asm:asm`) | Binary class files: type list, signatures, supertypes |
| Vineflower | Last-resort decompiling |
| (none) | CLI parsing and `--help` are hand-written in `cli` (picocli dropped in T14: startup cost, not AOT-cacheable) |
| slf4j-api + slf4j-simple | Logging, to **stderr only** |
| Lombok (provided) | Boilerplate reduction |
| JUnit Jupiter 6, AssertJ (test) | Tests. No Mockito: use hand-written fakes behind interfaces. |

## Package layout

All packages are below `io.github.vecluu.jdoc`. Each package has one owning task (see `tasks/README.md`).
Arrows show allowed dependencies; nothing may depend "upwards" (e.g. `index` must not import `cli`).

| Package | Owner | Responsibility | May depend on |
| --- | --- | --- | --- |
| `config` | T01 | Environment access and cache path layout | `model`, `error` |
| `model` | T01 | Immutable domain values shared by all layers | — |
| `error` | T01 | `JdocException` hierarchy | `model` |
| `module` | T02 | Find module, POM key, run Maven, parse `deps.txt`, meta cache, reactor detection, fallbacks | `config`, `model`, `error` |
| `index` | T03 | SQLite schema, repositories, file lock | `config`, `model`, `error` |
| `source` | T04 | javac Tree API parsing of `.java` text into model descriptors, signature rendering | `model`, `javadoc`, `error` |
| `javadoc` | T01 (shared text rules) · T04 (doc comments) · T05 (HTML pages) | Javadoc → compact plain text (`RenderedDoc`) | `model`, `error` |
| `bytecode` | T05 | ASM reading of binary jars and class files | `model`, `error` |
| `decompile` | T06 | Vineflower wrapper and decompiled-source cache | `config`, `model`, `error` |
| `text` | T07 | Output primitives: pagination, token budget, column alignment, header line | `model` |
| `query` | T07 | Query syntax and pure matching: `Type#name(params)` parsing, camel humps, overload matching | `model` |
| `indexer` | T08 | Lazy/forced indexing of artifacts and the JDK, per-type doc-source fallback, refresh | everything above except `query`, `text` |
| `lookup` | T09 | Read-side services: type resolution, hierarchy, find, impls, member search, on-demand docs and source | `index`, `source`, `javadoc`, `bytecode`, `decompile`, `query`, `model`, `config`, `error` |
| `app` | T10 | One application service per command, producing `CommandResult` | all of the above |
| `cli` | T10, T14 | hand-written command-line parser and help, exit codes, composition root | `app`, `config`, `text`; the composition root may import every package |

## Cross-cutting rules

- **Object-oriented design.** Single responsibility, small focused classes, interfaces at component boundaries
  (anything that touches the filesystem, a process, the database or a jar gets an interface so callers can be tested
  with fakes). Constructor injection only; no service locators, no static mutable state, no singletons. The only
  place that wires concrete classes together is the composition root in `cli` (T10).
- **Immutability.** Domain values are Java `record`s (or Lombok `@Value`). Collections in values are defensively
  copied and unmodifiable. Use `Optional` for absent return values, never return `null` from public methods.
- **Lombok.** Use where it removes noise: `@RequiredArgsConstructor` for services, `@Slf4j` for logging,
  `@Builder` for values with many fields, `@Value`/`@Getter` as needed. Do not use `@Data` on domain values,
  `@SneakyThrows`, or `val`/`var` from Lombok.
- **stdout is the product.** Only the `cli` layer writes to `System.out`. Everything else logs via slf4j (stderr).
  Default log level is `warn`; never log at info/debug in a way that would show without `-Dorg.slf4j.simpleLogger...`.
- **Errors.** Checked I/O and SQL exceptions are wrapped in the `JdocException` hierarchy at component boundaries.
  Expected outcomes (not found, ambiguous) are values, not exceptions.
- **Environment.** Never read `System.getenv`, `user.home` or the working directory directly outside `config`.
  Everything goes through `JdocEnvironment`, so tests can point the cache, `~/.m2`, `JAVA_HOME` and the Maven
  executable at temp directories.
- **Tests never touch** the real `~/.cache/jdoc` or `~/.m2`, and never need network. Use `@TempDir`.
- **Linux only**, UTF-8 everywhere.

## Configuration (`config`, T01)

`JdocEnvironment` — an immutable view of the process environment, built from a `Map<String,String>` of env vars
plus system properties, so tests can construct it freely. It exposes:

| Accessor | Source | Default |
| --- | --- | --- |
| cache directory | `JDOC_CACHE` | `$HOME/.cache/jdoc` |
| local Maven repository | `JDOC_M2_REPO` | `$HOME/.m2/repository` |
| Java home | `JAVA_HOME` | `java.home` system property |
| Maven executable | `JDOC_MVN` | `mvn` (resolved via `PATH`) |
| working directory | `user.dir` | — |

`CachePaths` — derives every path under the cache dir: `index.db`, `lock`, `modules/<key>/deps.txt`,
`modules/<key>/meta.json`, `decompiled/<groupPath>/<artifactId>/<version>/`. It creates directories on demand.

## Domain model (`model`, T01)

All are immutable records/values. Names are binding; extra helper methods are welcome.

| Type | Contents and rules |
| --- | --- |
| `Gav` | groupId, artifactId, version, optional classifier, type (default `jar`). `isSnapshot()`; `toString()` = `g:a:v`; `coordinates()` without version (`g:a`) for reactor matching. |
| `Scope` | enum COMPILE, PROVIDED, RUNTIME, TEST, SYSTEM; parse from Maven text. |
| `ResolvedDependency` | `Gav`, `Scope`, absolute jar `Path`. |
| `ResolutionStatus` | enum RESOLVED, STALE (Maven failed, last good list used), UNRESOLVED (newest-in-repo guess). |
| `ModuleClasspath` | module directory, POM key, list of `ResolvedDependency`, `ResolutionStatus`, list of warning strings. |
| `TypeName` | the **binary name** (`java.util.Map$Entry`). Derived: `packageName()`, `simpleName()` (`Entry`), `displayName()` (`java.util.Map.Entry`), `outerName()` (optional), `topLevel()`, `sourceEntryPath()` (`java/util/Map.java`), `classEntryPath()` (`java/util/Map$Entry.class`). Factory from binary name, and from package + nested simple names. |
| `TypeKind` | enum CLASS, INTERFACE, ENUM, RECORD, ANNOTATION; `keyword()` gives the Java keyword (`@interface` for annotations). |
| `MemberKind` | enum METHOD, CONSTRUCTOR, FIELD, ENUM_CONSTANT. |
| `Visibility` | enum PUBLIC, PROTECTED, PACKAGE, PRIVATE; `isApi()` is true for PUBLIC and PROTECTED. |
| `DocSource` | enum SOURCES, JAVADOC, DECOMPILED, JDK; `headerTag()` = none, `[from javadoc jar]`, `[decompiled, no docs]`, none. |
| `ArtifactKey` | identity of an indexed artifact: either a `Gav` or the JDK (with its feature version, e.g. `25`). `label()` = `commons-lang3 3.17.0` or `jdk 25`. |
| `SourceRange` | start and end character offsets into the source text (body and doc comment ranges for `jdoc source`). |
| `ParameterInfo` | parameter name, rendered type as declared (`List<String>`), erased simple type for matching (`List`; varargs as `String[]`; type variables replaced by the simple name of their first bound, else `Object`). |
| `TypeDescriptor` | `TypeName`, `TypeKind`, `Visibility`, modifiers text (e.g. `public final`), type parameters text (`<T>` or empty), direct supertypes (list of `TypeName`, superclass first), deprecated flag, first doc sentence (may be empty), `DocSource`, entry path inside its doc-source container, optional `SourceRange` of the declaration. |
| `MemberDescriptor` | declaring `TypeName`, name (`<init>` is **not** used: constructors use the simple type name), `MemberKind`, rendered signature (see below), list of `ParameterInfo`, `Visibility`, static flag, deprecated flag, first doc sentence, optional `SourceRange` for the whole member (doc comment through end of body) and optional `SourceRange` for the doc comment. |
| `RenderedDoc` | plain-text main description, first sentence, ordered list of block-tag lines (`@param x ...`, `@return ...`, `@throws ...`, `@since ...`, `@deprecated ...`), and a flag `inheritsDoc` that is true when the comment is missing or contains `{@inheritDoc}` that the renderer could not resolve itself. |
| `IndexedArtifact` | database id, `ArtifactKey`, jar path, jar mtime, jar size, sources-jar path (optional), javadoc-jar path (optional), indexed-at timestamp. |
| `IndexedType` | database id, artifact id, plus a `TypeDescriptor`. |
| `IndexedMember` | database id, type id, plus a `MemberDescriptor`. |

### Rendered signature format

One line, as the agent would write the declaration, without `public` and without annotations or bodies:

- methods: `[protected ][static ][abstract |default ]<type params> ReturnType name(Type a, Type b)[ throws X, Y]`,
  e.g. `static <T> Optional<T> of(T value)`
- constructors: `[protected ]Optional(T value)`
- fields: `[protected ][static ][final ]Type NAME`
- enum constants: `NAME`
- Types use simple names as written in source (`List<String>`, `Map.Entry<K,V>`), varargs as `String...`.
- From bytecode (no source): same format, simple names derived from descriptors/generic signatures,
  parameter names from the MethodParameters attribute, else `arg0`, `arg1`, ...

## Database (`index`, T03)

SQLite file `index.db`, WAL mode, `PRAGMA user_version` for schema versioning (on mismatch: drop and rebuild).
Tables follow `OUTLINE.md` with these refinements:

- `artifacts`: id, artifact key (unique: GAV string or `jdk:<version>`), jar path, mtime, size, sources jar,
  javadoc jar, indexed-at.
- `types`: id, artifact id, binary name, simple name, package, kind, visibility, modifiers, type params, deprecated,
  first sentence, **doc source per type**, entry path, declaration offsets. Indexes on binary name, simple name
  (case-insensitive), package.
- `supertypes`: type id, supertype binary name. Index on supertype binary name (for `impls`).
- `members`: id, type id, name, kind, signature, parameter list (stored as a compact text encoding), visibility,
  static, deprecated, first sentence, member offsets, doc offsets. Index on name (case-insensitive).
- Deleting an artifact cascades to its types, supertypes and members.

Every read query is scoped to a **set of artifact ids** (the current module's classpath plus the JDK).

## Runtime flow (one call)

1. `cli` parses arguments; composition root builds services from `JdocEnvironment`.
2. `module.ClasspathResolver` → `ModuleClasspath` (cache hit: read `deps.txt`; miss: run Maven).
3. `indexer.ClasspathIndexer` ensures the JDK and every jar on the classpath is indexed and current
   (stat mtime/size; re-index only changed jars; under the file lock).
4. `app` service for the command uses `lookup` services scoped to those artifact ids.
5. Result is a `CommandResult` (exit code + lines); `cli` paginates with `text` and prints.

## Exit codes

`0` found, `1` not found, `2` ambiguous (candidates printed), `3` error (e.g. failed resolution with no fallback,
corrupt jar, unexpected exception). Exit code `3` also prints one concise error line to stderr.
