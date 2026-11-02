# jdoc: Java library docs CLI for agents

Oct 28, 2026 · @E

## Goal and decisions

`jdoc` is a Java CLI that gives an opencode agent the exact signature and Javadoc of a library method in a few hundred tokens, at the version the current module actually uses. It replaces guessing and decompiling, and runs fully offline.

| Topic | Decision |
| --- | --- |
| Agent | opencode with shell access; ships as a skill (SKILL.md) plus an AGENTS.md section |
| Interface | CLI, not an MCP server; nothing sits in context until the skill is used |
| Language | Java 25; sources parsed with the JDK's own javac Tree API (\`jdk.compiler\`), one fat jar |
| OS | Linux only |
| Jar source | Local Maven repo (`~/.m2/repository`) |
| Versions | Per module, resolved by `mvn -o dependency:list`; parent POMs and BOMs are handled by Maven |
| Scopes | compile, provided, runtime, test (all resolve offline) |
| Fallback chain | sources jar, then javadoc jar, then decompile |
| JDK classes | Included, from the module JDK's `lib/src.zip` |
| Library languages | Java only; .kt or .scala sources count as missing sources |
| Visibility | public and protected |
| Inherited members | Names grouped by declaring type; inherited protected members (incl. static) are hidden for types that cannot be subclassed (final, enum, record, sealed without a non-sealed subtype), and `jdoc method` on them exits 1 with a hint |
| Ambiguous simple name | List candidate FQCNs, no docs |
| Output cap | About 2,000 tokens per call, then `--page N` |
| Refresh | Automatic on change, plus `jdoc index --force` |
| Cache | `~/.cache/jdoc` only; nothing written into projects |
| Startup | Warm calls answer from SQLite only; JDK 25 AOT cache trained at install; no picocli |

## Architecture

&#91;embedded content: jdoc lookup path · 8 components\]

Maven runs only when a module's POM key changes; every other call is an index lookup plus one jar entry read. The doc source is chosen per type, so one artifact can mix all three.

## Version resolution

Maven resolves the versions; jdoc never reimplements parent, BOM or `dependencyManagement` logic. Each module's resolved list is cached and only recomputed when one of its POMs changes.

1. **Find the module.** Walk up from `--from <file>` (default: the working directory) to the nearest `pom.xml`. That directory is the module.
2. **Check the cache.** The key is a SHA-256 over the module POM plus every local ancestor POM reached through `<relativePath>`. Parent POMs from the repo are fixed per release version, so only SNAPSHOT parents add their file mtime to the key.
3. **Resolve on a miss.** Run in the module directory:

```
mvn -o -q -B dependency:list \
  -DincludeScope=test \
  -DoutputAbsoluteArtifactFilename=true \
  -DoutputFile=~/.cache/jdoc/modules/<key>/deps.txt
```

`includeScope=test` yields the full test classpath, which already contains compile, provided and runtime. Each line gives the GAV, scope and absolute jar path.

For a module that depends on a sibling reactor module (or when the plain run fails and a reactor root exists), jdoc runs from the reactor root instead: `mvn -o -q -B test-compile dependency:list -Dmaven.main.skip=true -Dmaven.test.skip=true -Dmaven.resources.skip=true -pl <module> -am` with the same `-D` options. Maven 3 only resolves an uninstalled sibling once the lifecycle reaches `compile`/`test-compile`; the skip flags mean nothing is compiled and no `target/` is created, but plugins bound up to `test-compile` must be available offline. If offline Maven cannot map the `dependency:` prefix (plugin jar present, group metadata missing), jdoc retries with the fully qualified goal of the newest locally installed maven-dependency-plugin.

4. **Handle failure.** If Maven fails, jdoc uses the last good list for that module and prints one warning line. With no list at all, lookups use the newest version found in the repo and mark the result `[version unresolved]`.
5. **Skip the project's own modules.** Artifacts that belong to the current reactor are left out; the agent reads those as normal source files.

The JDK is taken from `JAVA_HOME` (Java 25), indexed from its `lib/src.zip`, and shared by every module.

## Index and cache

The index stores where each type and member lives, its first doc sentence and its full rendered Javadoc. Docs are rendered once at index time, so lookups never start javac or jsoup; this was chosen over a smaller index because JVM startup dominates every call (see `docs/decisions/startup.md`). The index stays faithful to the jar because a changed jar is re-indexed. Only `jdoc source` still reads the jar entry (or decompiles) on demand.

```
~/.cache/jdoc/
  index.db                  SQLite, WAL mode
  modules/<key>/deps.txt    resolved classpath per module
  modules/<key>/meta.json   module path, POM hashes, resolved-at
  module-snapshots/         file stamps per module; skips POM parsing and hashing while nothing changed
  cds/jdoc.jsa              fallback CDS archive, used by the wrapper only when the AOT cache does not match
  decompiled/<g>/<a>/<v>/   Vineflower output, written once
  lock                      held while indexing
```

| Table | Columns |
| --- | --- |
| `artifacts` | gav, jar path, jar mtime and size, doc source (sources, javadoc, decompiled, jdk) |
| `types` | fqcn, simple name, package, kind (class, interface, enum, record, annotation), visibility, doc source, first doc sentence, rendered doc, entry path |
| `supertypes` | type, direct supertype fqcn (feeds inherited members and `impls`) |
| `members` | type, name, kind (method, constructor, field, enum constant), rendered signature, parameters, visibility, deprecated flag, source offsets, first doc sentence, rendered doc |
| `packages` | package, artifact, rendered `package-info` doc |

**Indexing is lazy.** An artifact is indexed the first time a resolved module needs it, never the whole repo. The JDK's `src.zip` is indexed once on first use.

**Refresh rules:**

- Every call re-hashes the module's POM key; a change triggers re-resolution.
- Every call stats the jars on the module's list; a new or changed jar (mtime or size) is re-indexed alone.
- `jdoc index` resolves and indexes the current module up front; `--force` rebuilds its artifacts from scratch.
- A file lock stops two agents indexing the same artifact at once; the second waits, then reads.

## Doc sources and fallback

The fallback runs per type, not per artifact. The binary jar is the list of what exists; each type then takes the best doc source available. This also covers generated classes that are missing from an otherwise complete sources jar.

| Order | Source | When used | Result header tag |
| --- | --- | --- | --- |
| 1 | `<a>-<v>-sources.jar`, parsed by the javac Tree API, Javadoc via \`DocTrees\` | The type's `.java` file is present | none |
| 2 | `<a>-<v>-javadoc.jar` HTML, parsed by jsoup; signatures read from the binary jar with ASM | No `.java` file, but the type's HTML page exists | `[from javadoc jar]` |
| 3 | Vineflower decompile of the binary class, cached | Neither of the above | `[decompiled, no docs]` |
| JDK | `$JAVA_HOME/lib/src.zip`, same path as 1 | Any `java.*`, `javax.*`, `jdk.*` type | none |

**Javadoc rendering** turns comments into compact plain text:

- `{@code x}` and `<code>` become backticks; `{@link Foo#bar(int)}` becomes `Foo#bar(int)`.
- `<p>` becomes a blank line, `<pre>` blocks are kept, other HTML tags are stripped.
- `@param`, `@return`, `@throws`, `@since` and `@deprecated` stay as one line each.
- `{@inheritDoc}` and undocumented overrides take the doc from the nearest supertype that has one.

**Other rules:**

- A type whose only source file is `.kt` or `.scala` counts as missing and drops to step 2.
- Javadoc HTML parsing targets the JDK 11+ page layout. Older layouts get signatures from ASM and a doc only where the anchors match.
- Decompiled bodies are shown only through `jdoc source`; other commands show their signatures.

## Commands and output

Eight commands, all plain text, all answering from the current module's classpath plus the JDK. Every result starts with one header line naming the type and the artifact version it came from.

| Command | Returns |
| --- | --- |
| `jdoc find <pattern>` | Matching types and members as FQCNs, one line each; case-insensitive substring or camel humps (`HSR` matches `HttpServletRequest`); at equal match score the module's libraries rank before the JDK |
| `jdoc class <type>` | Kind, generics, supertypes, first doc paragraph; each public and protected member as signature plus first sentence; inherited member names by declaring type |
| `jdoc method <type>#<name>[(<params>)]` | Full rendered Javadoc for the method, or for every overload when no params are given |
| `jdoc source <type>#<name>[(<params>)]` | The method body with its Javadoc; the only command that shows implementation |
| `jdoc impls <type>` | Known subtypes or implementations on the classpath, with artifact |
| `jdoc member <name>` | Every type on the classpath declaring a member with that name, as `Type#name(params)` |
| `jdoc package <pkg>` | `package-info` doc, then its types with first sentences |
| `jdoc index [--force]` | Resolves and indexes the current module; prints counts and any types with missing sources |

**Names and flags**

- `<type>` is an FQCN or a simple name. Nested types accept `Outer.Inner` or `Outer$Inner`.
- `<params>` are simple type names, comma separated: `#of(int,int)`. Generic parameters are erased for matching.
- `--from <path>` picks the module from a file's location instead of the working directory.
- `--page <n>` continues a capped result.

**Exit codes** let the skill react without parsing text: `0` found, `1` not found, `2` ambiguous (candidates printed), `3` error such as a failed resolution with no fallback.

**Output cap:** about 2,000 tokens per call, estimated as characters divided by 4. A capped result ends with a line like `page 1/3 · next: jdoc class java.util.Optional --page 2`.

Example, an ambiguous simple name (exit `2`):

```
$ jdoc class Duration
2 types named Duration on this classpath:
  java.time.Duration              jdk 25
  javax.xml.datatype.Duration     jdk 25
Re-run with a fully qualified name.
```

Example, a class view (shortened):

```
$ jdoc class java.util.Optional
java.util.Optional<T>  public final class  (jdk 25)
A container object which may or may not contain a non-null value.

static <T> Optional<T> empty()               Returns an empty Optional instance.
static <T> Optional<T> of(T value)           Returns an Optional describing the given non-null value.
static <T> Optional<T> ofNullable(T value)   Returns an Optional describing the given value, if non-null, otherwise an empty Optional.
…
T orElseThrow()                              If a value is present, returns the value, otherwise throws NoSuchElementException.
inherited from Object: getClass, notify, notifyAll, wait
```

## Skill and AGENTS.md

The skill carries the rules that stop guessing; AGENTS.md only makes sure the agent knows the skill exists. The skill's description is what opencode keeps in context, so it names the trigger plainly.

The build is the primary signature check: compiling is free and catches wrong signatures. jdoc is for APIs the model does not know well (internal, niche or new major versions, unknown class or method names), for the first compile error on a library call (never guess twice), and for behaviour the compiler cannot check (null handling, exceptions, mutability, thread safety, defaults, deprecation). It is not needed for common JDK APIs or for calls the project's own code already makes. The reasoning is in `docs/decisions/skill-design.md`.

The files themselves are `dist/skill/SKILL.md` (installed into the opencode skills directory) and `dist/AGENTS-snippet.md` (pasted into a repo's `AGENTS.md`); they are the source of truth and are checked against the real `--help`. The embedded drafts that used to be here told the agent to call jdoc before every unverified library method, which over-triggers, and were replaced by T24.

## Build and airgap transfer

A GitHub Actions pipeline (next section) builds one release tarball holding the jar, wrapper, installer and skill files; you carry it into the airgap and run its installer. Parsing uses the JDK's javac Tree API rather than JavaParser: it always understands exactly the syntax of the JDK it runs on (Java 25, including the JDK's own `src.zip`) and parses Javadoc into a proper tree.

| Dependency | Purpose |
| --- | --- |
| `jdk.compiler` (in the JDK) | Parse `.java` files and Javadoc comments, parse only, no type attribution |
| sqlite-jdbc | The index; bundles its native library for Linux x86\_64 and aarch64 |
| jsoup | Javadoc jar HTML |
| ASM | Signatures and supertypes from binary class files |
| Vineflower | Last-resort decompiling |
| slf4j (simple) | Logging to stderr |

Command-line parsing is hand-written (picocli was dropped: it cannot be stored in the AOT cache and cost ~100 ms per call).

Installing a release:

1. Download `jdoc-<version>.tar.gz` and `SHA256SUMS` from the GitHub release. While still online, run `gh attestation verify jdoc-<version>.tar.gz -R <owner>/jdoc`.
2. Transfer both files, then run `sha256sum -c SHA256SUMS` inside the airgap.
3. Run `tar xzf jdoc-<version>.tar.gz && sudo ./jdoc-<version>/install.sh --skills-dir <opencode skills dir>`.
4. Paste `AGENTS-snippet.md` into each repo's `AGENTS.md`.
5. Run `jdoc index` once in a real module and check the missing-sources report.

JVM startup is paid on every call and dominates it (~590 ms warm before tuning). The installer therefore pre-extracts the sqlite native library and trains a JDK 25 AOT cache; the wrapper uses it when its fingerprint still matches the jar and JDK, and otherwise falls back silently to a self-maintaining CDS archive. Measurements and options are in `docs/decisions/startup.md`.

## CI pipeline (GitHub Actions)

Every pull request and push to `main` builds, unit-tests and smoke-tests jdoc offline. A `v*` tag also publishes a GitHub release with the tarball, `SHA256SUMS` and signed build provenance. The bundle needs no network at install time.

Repository layout:

```
jdoc/
  pom.xml                       version comes from ${revision}
  src/main/java/                the CLI
  src/test/java/                unit tests
  it/sample-project/pom.xml     small Maven project for the smoke test
  dist/bin/jdoc                 wrapper template
  dist/install.sh               installer
  dist/skill/SKILL.md
  dist/AGENTS-snippet.md
  scripts/assemble.sh           builds the tarball
  scripts/smoke-test.sh         offline end-to-end check
  scripts/install-test.sh       installs the assembled tarball into temp dirs and checks it
  scripts/bench-startup.sh      startup benchmark (median per command, incl. first call after adding a dependency)
  .github/workflows/build.yml
```

Release bundle, plus `SHA256SUMS` beside it:

```
jdoc-1.2.0/
  VERSION
  LICENSE
  NOTICE
  THIRD-PARTY-NOTICES
  install.sh
  AGENTS-snippet.md
  bin/jdoc
  lib/jdoc.jar
  skill/SKILL.md
```

`.github/workflows/build.yml`:

> Release path (T27): the jar and tarball are reproducible from `SOURCE_DATE_EPOCH` (commit time in CI); the release job uploads only the tarball and `SHA256SUMS`, checks the checksum and that the tag matches the version, attests the tarball, and uses the `CHANGELOG.md` section (via `scripts/release-notes.sh`) as release notes. A `workflow_dispatch` input `release-dry-run=<version>` runs the release job without attesting or publishing.
>
> Implemented by T18 in `.github/workflows/build.yml`; the draft below is historical. The real workflow builds with `./mvnw`, pins actions to commit SHAs, runs shellcheck on all scripts, runs the install test from the assembled tarball (launcher also exercised under `dash`) and the smoke test through the installed launcher. The installer copies `LICENSE`, `NOTICE` and `THIRD-PARTY-NOTICES` to `<prefix>/share/doc/jdoc/`.

```yaml
name: build
on:
  pull_request:
  push:
    branches: [main]
    tags: ['v*']
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v5
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '25'
          cache: maven

      - name: Version from tag, else snapshot
        run: |
          if [[ "$GITHUB_REF" == refs/tags/v* ]]; then
            echo "VERSION=${GITHUB_REF_NAME#v}" >> "$GITHUB_ENV"
          else
            echo "VERSION=0.0.0-${GITHUB_SHA::7}" >> "$GITHUB_ENV"
          fi

      - name: Build and unit test
        run: mvn -B -ntp -Drevision="$VERSION" verify

      - name: Offline smoke test
        run: scripts/smoke-test.sh target/jdoc.jar it/sample-project

      - name: Assemble bundle
        run: scripts/assemble.sh "$VERSION"

      - uses: actions/upload-artifact@v4
        with:
          name: jdoc-${{ env.VERSION }}
          path: out/

  release:
    if: startsWith(github.ref, 'refs/tags/v')
    needs: build
    runs-on: ubuntu-24.04
    permissions:
      contents: write
      id-token: write
      attestations: write
    steps:
      - uses: actions/download-artifact@v4
        with:
          path: out
          merge-multiple: true
      - uses: actions/attest-build-provenance@v2
        with:
          subject-path: out/jdoc-*.tar.gz
      - name: Publish release
        env:
          GH_TOKEN: ${{ github.token }}
        run: gh release create "$GITHUB_REF_NAME" out/* --generate-notes -R "$GITHUB_REPOSITORY"
```

Write permissions sit only on the `release` job, which runs only for tags, so pull requests never get a token that can publish. Pin each action to a commit SHA if your policy requires it.

`scripts/assemble.sh` builds a reproducible tarball (fixed order, owner and timestamps):

```bash
#!/usr/bin/env bash
set -euo pipefail
v=$1; name=jdoc-$v; stage=out/$name
export SOURCE_DATE_EPOCH=$(git log -1 --format=%ct)

rm -rf out && mkdir -p "$stage"/{bin,lib,skill}
cp target/jdoc.jar "$stage/lib/"
cp dist/bin/jdoc "$stage/bin/"
cp dist/install.sh dist/AGENTS-snippet.md "$stage/"
cp dist/skill/SKILL.md "$stage/skill/"
echo "$v" > "$stage/VERSION"
chmod 755 "$stage/bin/jdoc" "$stage/install.sh"

tar -C out --sort=name --mtime="@$SOURCE_DATE_EPOCH" \
    --owner=0 --group=0 --numeric-owner -cf - "$name" \
  | gzip -n > "out/$name.tar.gz"
rm -rf "$stage"
(cd out && sha256sum "$name.tar.gz" > SHA256SUMS)
```

`scripts/smoke-test.sh` fetches the sample project's dependencies and sources once into a private temporary Maven repository (never `~/.m2`), then runs jdoc offline the way the airgap will, through the installed launcher: every command, `--help`, `--version`, `--page`, `--from`, all three doc-source headers and exit codes 0–3. It needs one small addition to the CLI: a `JDOC_CACHE` environment variable that overrides `~/.cache/jdoc`.

```bash
#!/usr/bin/env bash
set -euo pipefail
jar=$(realpath "$1"); proj=$2
export JDOC_CACHE=$(mktemp -d)
jdoc() { java -jar "$jar" "$@"; }

# prime ~/.m2 with jars and sources jars, as the airgap repo would be
mvn -B -ntp -f "$proj/pom.xml" dependency:go-offline dependency:sources

cd "$proj"
jdoc index
jdoc method java.util.Optional#orElseThrow
jdoc method org.apache.commons.lang3.StringUtils#isBlank

set +e; jdoc class Duration > /dev/null; rc=$?; set -e
[ "$rc" -eq 2 ] || { echo "expected exit 2 for an ambiguous name, got $rc"; exit 1; }
echo "smoke test passed"
```

`dist/install.sh`, run inside the airgap:

> Implemented by T17: installs into `<prefix>/lib/` (`jdoc.jar`, the extracted sqlite native library, `jdoc.aot` cache of ~44 MB and `jdoc.aot.key`), trains the AOT cache in two steps (~8.4 s) against throwaway modules with a fake `mvn` and a temp cache: JDK lookups plus a tiny library generated at install time with javac/javadoc in three flavours (sources jar, javadoc jar only, binary only), so indexing a new dependency and decompiling are trained too (T23) (training is triggered by the hidden system property `-Djdoc.aotTraining`, not a CLI command), and only warns if training fails.

```bash
#!/usr/bin/env bash
set -euo pipefail
prefix=/opt/jdoc; bindir=/usr/local/bin; skills=""
while [ $# -gt 0 ]; do
  case $1 in
    --prefix)     prefix=$2; shift 2 ;;
    --bindir)     bindir=$2; shift 2 ;;
    --skills-dir) skills=$2; shift 2 ;;
    *) echo "unknown option: $1" >&2; exit 1 ;;
  esac
done
here=$(cd "$(dirname "$0")" && pwd)

java -version 2>&1 | grep -q 'version "25' \
  || { echo "jdoc needs Java 25 on PATH" >&2; exit 1; }

install -Dm644 "$here/lib/jdoc.jar" "$prefix/jdoc.jar"
sed "s|@JAR@|$prefix/jdoc.jar|" "$here/bin/jdoc" > "$bindir/jdoc"
chmod 755 "$bindir/jdoc"
[ -n "$skills" ] && install -Dm644 "$here/skill/SKILL.md" "$skills/jdoc/SKILL.md"
echo "installed jdoc $(cat "$here/VERSION")"
```

`dist/bin/jdoc`, the wrapper template the installer fills in:

> Implemented by T17; the real files are `dist/bin/jdoc` and `dist/install.sh`, and the draft below is historical. The wrapper adds `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:-UsePerfData`, points sqlite-jdbc at the pre-extracted native library, and passes `-XX:AOTCache` only when the fingerprint in `<prefix>/lib/jdoc.aot.key` (jar, `java` binary, JDK `release` and `lib/modules` stats) still matches; otherwise it falls back silently to `${JDOC_CACHE}/cds/jdoc.jsa`. JVM logging is routed to stderr with cache warnings off, so stdout stays the answer. `JDOC_JAVA_OPTS` adds JVM options; `JDOC_WRAPPER_DEBUG=1` prints the chosen mode to stderr.

```sh
#!/bin/sh
exec java -XX:TieredStopAtLevel=1 -jar @JAR@ "$@"
```

## Open questions

Two points could change the design and need a quick test in the airgap; the third only affects fallback quality.

- [x] **Sibling modules.** Answered (T19, Maven 3.10.0): `dependency:list` in a submodule fails when a sibling is not installed, and so does `-pl <module> -am` alone; the `test-compile` reactor form in step 3 works. Original question: Does `mvn -o dependency:list` succeed inside a submodule when its sibling modules are not installed in `~/.m2`? Maven 3 often fails there. If it does, jdoc runs from the reactor root with `-pl <module> -am` instead. Which Maven version is installed?
- [x] **Resolution time.** Answered (T19): `dependency:list` takes ~1.5–1.9 s on jdoc itself and a small multi-module reactor; a cold jdoc call ~1.7–2.0 s, a cached one ~0.25 s. No git hook needed at this size. Original question: How long does `mvn -o dependency:list` take on your largest module? Over a few seconds is fine for a cache miss, but would argue for `jdoc index` in a git hook.
- [x] **Old javadoc jars.** Answered (T19, commons-lang3 3.5 built with JDK 8 javadoc): every member's doc matched (214/214 StringUtils, 307/307 ArrayUtils); only parameter names fall back to `arg0`… (follow-up: take names from the HTML). Original question: How many of your javadoc-only artifacts were built with JDK 8 javadoc? Those pages get signatures but often no matched docs.
