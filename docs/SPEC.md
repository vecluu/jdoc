# jdoc specification

What jdoc does, why, and what it does not do. This is the current behaviour, not a plan. The code structure is in
`docs/ARCHITECTURE.md`; user documentation is `README.md`.

`jdoc` is a Java CLI that gives a coding agent the exact signature and Javadoc of a library or JDK member in a few
hundred tokens, at the version the current Maven module actually uses. It replaces guessing and decompiling, and runs
fully offline.

## Decisions

| Topic | Decision | Why |
| --- | --- | --- |
| Agent | opencode with shell access; ships as a skill (`SKILL.md`) plus an `AGENTS.md` snippet | the skill's description is all that sits in context until it is used |
| Interface | CLI, not an MCP server | nothing in context until called; works from any shell |
| Language | Java 25, one shaded jar | the JDK 25 AOT cache and javac Tree API of the running JDK |
| Parsing | the JDK's own javac Tree API (`jdk.compiler`), parse only | always understands exactly the syntax of the JDK it runs on (including its own `src.zip`) and parses Javadoc into a tree; no JavaParser |
| OS | Linux only | the airgapped target; launcher relies on GNU/busybox `stat` |
| Jar source | the local Maven repository | the airgap has nothing else |
| Versions | per module, resolved by `mvn -o dependency:list` | Maven owns parents, BOMs and `dependencyManagement`; jdoc never reimplements them |
| Scopes | compile, provided, runtime, test | the test classpath contains them all and resolves offline |
| Doc sources | per type: sources jar, then javadoc jar, then decompile | generated classes missing from a sources jar still get the best available view |
| JDK | indexed from the module JDK's `lib/src.zip` | same parser path as sources jars |
| JDK precedence | in type resolution the JDK comes first, then the classpath in order | as javac does: a library type with a JDK binary name never shadows the JDK's |
| Visibility | public and protected only | the API an agent may call |
| Docs storage | full rendered docs are stored at index time | JVM start dominates every call; parsing (javac) and rendering (jsoup) per lookup cost more than the query. Costs a bigger index |
| CLI parsing | hand-written, no picocli | picocli could not be stored in the AOT cache and cost about 100 ms per call |
| Startup | warm calls answer from SQLite only; JDK 25 AOT cache trained at install | a daemon or native-image was rejected (lifecycle, build complexity); see `docs/decisions/startup.md` |
| Output cap | about 2,000 tokens per call, then `--page N` | an answer stays in the agent's context for the rest of the session |
| Cache | `~/.cache/jdoc` only; nothing written into projects | |

## Version resolution

1. **Find the module:** the nearest directory with a `pom.xml`, walking up from `--from <file>` (default: the working
   directory). Its real path is the module identity.
2. **Check the cache.** The key is a SHA-256 over the module POM plus every local ancestor POM reached through
   `<relativePath>`; repository parents are fixed per release version, so only SNAPSHOT parents add their mtime.
   While no file the POM analysis touched changed its stamp, the POMs are not even parsed.
3. **Resolve on a miss** in the module directory:
   `mvn -o -q -B dependency:list -DincludeScope=test -DoutputAbsoluteArtifactFilename=true -DoutputFile=<cache>/modules/<key>/deps.txt`
   (plus `-Dmaven.repo.local` when `JDOC_M2_REPO` is set). For a module that depends on an uninstalled sibling, or
   when the plain run fails and a reactor root exists, jdoc runs from the reactor root with
   `test-compile dependency:list -Dmaven.main.skip=true -Dmaven.test.skip=true -Dmaven.resources.skip=true -pl <module> -am`:
   Maven 3 resolves an uninstalled sibling only once the lifecycle reaches `test-compile`; nothing is compiled. If
   offline Maven cannot map the `dependency:` prefix, jdoc retries with the fully qualified goal of the newest local
   maven-dependency-plugin.
4. **Handle failure:** use the last good list for the module and print one warning line (STALE); with no list, use
   the newest version in the local repository and mark results `[version unresolved]` (UNRESOLVED).
5. **Skip the project's own modules:** reactor artifacts are left out; the agent reads those as source files.

## Index, cache and refresh

```
~/.cache/jdoc/                (or $JDOC_CACHE)
  index.db                  SQLite, WAL: artifacts, types, supertypes, members, rendered docs, failed artifacts
  modules/<key>/deps.txt    resolved classpath per module
  modules/<key>/meta.json   module path, POM hashes, resolved-at
  module-snapshots/         file stamps per module; skips POM parsing while nothing changed
  cds/jdoc.jsa              fallback CDS archive, used by the launcher only when the AOT cache does not match
  decompiled/<g>/<a>/<v>/   Vineflower output, written once
  lock                      held while indexing
```

- **Lazy:** an artifact is indexed the first time a resolved module needs it, never the whole repository. The JDK is
  indexed once per cache.
- Every call re-checks the module's POM key (a change triggers resolution) and stats the jars on its list (a new or
  changed jar, by mtime or size, is re-indexed alone).
- A jar that failed to index is remembered and skipped, with one warning per call, until it changes or `--force`.
- `jdoc index` resolves and indexes the current module up front; `--force` rebuilds its artifacts.
- A file lock stops two processes indexing at once; the second waits, then reads.
- `{@inheritDoc}` and undocumented overrides are resolved at lookup time over the stored docs, because supertypes may
  live in other artifacts.
- Only `jdoc source` reads a jar entry (or decompiles) on demand.

## Doc sources

| Order | Source | When used | Header tag |
| --- | --- | --- | --- |
| 1 | `<a>-<v>-sources.jar`, javac Tree API, Javadoc via `DocTrees` | the type's `.java` file is present | none |
| 2 | `<a>-<v>-javadoc.jar` HTML (jsoup); signatures from the binary jar (ASM) | no `.java` file, the type's HTML page exists | `[from javadoc jar]` |
| 3 | binary class only; body by Vineflower decompile, cached | neither of the above | `[decompiled, no docs]` |
| JDK | `$JAVA_HOME/lib/src.zip`, as 1; without `src.zip`, bytecode from `jrt:/` | `java.*`, `javax.*`, `jdk.*` (exported, non-internal) | none |

- The binary jar is the list of what exists; each type takes the best source available.
- A type whose only source is `.kt` or `.scala` counts as missing.
- Javadoc HTML: JDK 8 and JDK 11+ page layouts. Parameter names come from the page when the member's section matches
  exactly; otherwise they stay as in bytecode (`MethodParameters`, else `arg0`, `arg1`, ...).
- Decompiled bodies are shown only by `jdoc source`; other commands show their signatures.

**Javadoc rendering** to compact plain text: `{@code x}` and `<code>` become backticks; `{@link Foo#bar(int)}` becomes
`Foo#bar(int)`; `<p>` becomes a blank line; `<pre>` is kept; other tags are stripped; `@param`, `@return`, `@throws`,
`@since`, `@deprecated` stay one line each.

## Commands

All plain text, answering from the current module's classpath plus the JDK. Every result starts with one header line
naming the type and the artifact version it came from (`commons-lang3 3.17.0`, `jdk 25`), plus a doc-source tag.

| Command | Returns |
| --- | --- |
| `jdoc find <pattern>` | matching types and members, one line each; case-insensitive substring, or camel humps when the pattern has an upper-case letter (`HSR` → `HttpServletRequest`); at equal score the module's libraries rank before the JDK |
| `jdoc class <type>` | kind, generics, supertypes, first doc paragraph; each public and protected member as signature plus first sentence; inherited member names by declaring type |
| `jdoc method <type>#<name>[(<params>)]` | full rendered Javadoc of the method, or of every overload without params |
| `jdoc source <type>#<name>[(<params>)]` | the member's source with its Javadoc; the only command that shows implementation |
| `jdoc impls <type>` | known subtypes and implementations on the classpath, with artifact |
| `jdoc member <name>` | every type declaring a member with that name, as `Type#name(params)` |
| `jdoc package <pkg>` | `package-info` doc, then its types with first sentences |
| `jdoc index [--force]` | resolves and indexes the current module; prints counts and types with missing sources |

- `<type>` is an FQCN or a simple name; nested types as `Outer.Inner` or `Outer$Inner`. An ambiguous simple name
  lists the candidate FQCNs (exit 2).
- `<params>` are simple type names, comma separated (`#of(int,int)`); generics are erased for matching.
- Options: `--from <path>` (module of that file), `--page <n>`, `--force` (index only), `--help`, `--version`.
  Options follow the command.
- Inherited members: names grouped by declaring type; inherited protected members are hidden for types that cannot be
  subclassed (final, enum, record, sealed without a non-sealed subtype); `jdoc method` on one exits 1 with a hint.
- **Exit codes:** `0` found, `1` not found (also a page past the end), `2` ambiguous (candidates printed), `3` error
  (failed resolution with no fallback, corrupt jar, unexpected exception; one `error:` line on stderr).
- **Paging:** about 2,000 tokens per page (characters / 4). A capped result ends with a line such as
  `page 1/3 · next: jdoc class java.util.Optional --page 2`.
- stdout is the answer only; warnings and errors go to stderr.

### Signature format

One line, as the agent would write the declaration, without `public`, annotations or bodies:

- methods: `[protected ][static ][abstract |default ]<type params> ReturnType name(Type a, Type b)[ throws X, Y]`,
  e.g. `static <T> Optional<T> of(T value)`; constructors: `[protected ]Optional(T value)`;
  fields: `[protected ][static ][final ]Type NAME`; enum constants: `NAME`.
- No implicit interface modifiers: interface and annotation methods never show `abstract`, their fields never show
  `static final`; `default` and `static` interface methods keep their keyword.
- Types use simple names as written (`List<String>`, `Map.Entry<K,V>`), varargs as `String...`. Signatures from
  bytecode use the same format and are identical to the source rendering.

## Environment

| Variable | Default | Purpose |
| --- | --- | --- |
| `JDOC_CACHE` | `~/.cache/jdoc` | index and cache directory |
| `JDOC_M2_REPO` | `~/.m2/repository` | local Maven repository (also passed to Maven when set) |
| `JDOC_MVN` | `mvn` on `PATH` | Maven executable |
| `JAVA_HOME` | the running JVM | JDK whose `lib/src.zip` is indexed |
| `JDOC_JAVA_OPTS` | none | extra JVM options (launcher only) |
| `JDOC_WRAPPER_DEBUG` | unset | `1` prints the launcher's cache mode on stderr |

## Distribution and startup

- A release is `jdoc-<version>.tar.gz` (jar, launcher, installer, skill files, licence files) plus `SHA256SUMS`, with
  signed build provenance. The build is reproducible from `SOURCE_DATE_EPOCH`. It installs offline.
- The installer pre-extracts sqlite's native library and trains a JDK 25 AOT cache against throwaway modules (JDK
  lookups plus a generated library in three flavours, so indexing and decompiling are trained too). A rare JDK 25
  crash while creating the cache is retried once without training data; if that fails, it warns.
- The POSIX launcher passes the AOT cache only while its fingerprint (jar, `java`, JDK) still matches, else falls back
  silently to a self-maintaining CDS archive.

## Skill policy

The build is the primary signature check. The skill tells the agent to call jdoc for unfamiliar libraries or
versions, for discovery, after the first compile error on a library symbol (never guess twice), and for behaviour the
compiler cannot check; not for common JDK calls or calls the project already makes. The files are
`dist/skill/SKILL.md` and `dist/AGENTS-snippet.md`; the snippet is never stricter than the skill. Reasoning:
`docs/decisions/skill-design.md`.

## Known limitations

The current gaps. `CHANGELOG.md` copies this list into each release; the backlog is in `tasks/README.md`.

- Linux only, and Java 25 must be the `java` on `PATH`. Tested on x86_64; aarch64 is untested.
- Maven projects only; dependencies must already be in the local repository. Docs need `-sources.jar` or
  `-javadoc.jar` files there; otherwise only decompiled signatures are shown.
- Java sources only: Kotlin or Scala sources count as missing sources.
- Options must follow the command (`jdoc class X --page 2`, not `jdoc --page 2 class X`).
- `jdoc source` on a JDK without `lib/src.zip` cannot show source.
- The fallback CDS archive holds only the classes of the call that created it, so the fallback is slower when that
  call was a trivial one such as `--version`.
- If AOT training fails twice, the install works but every call uses the slower fallback archive.
- The classpath cache key ignores `settings.xml`, BOM imports and repository release parents; a jar deleted from the
  local repository is silently dropped on a cache hit.
