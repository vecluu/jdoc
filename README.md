# jdoc

[![build](https://github.com/vecluu/jdoc/actions/workflows/build.yml/badge.svg)](https://github.com/vecluu/jdoc/actions/workflows/build.yml)

jdoc is an offline command-line tool that shows a coding agent the documentation of the Java libraries and JDK
classes it is using. It answers at the exact versions the current Maven module resolves. One call returns an exact
signature with its Javadoc in a few hundred tokens, so the agent does not guess method names or unzip and decompile
jars from `~/.m2`. It ships with a skill for [opencode](https://opencode.ai) and a short `AGENTS.md` snippet. It runs
on Linux with Java 25 and needs no network.

## Example

These outputs come from the built jar, run in [`it/sample-project`](it/sample-project). That module depends on
commons-lang3 3.20.0 (with a sources jar), commons-codec 1.19.0 (javadoc jar only) and jspecify 1.0.0 (binary only).

```
$ jdoc method 'org.apache.commons.lang3.StringUtils#abbreviate(String,int)'
org.apache.commons.lang3.StringUtils  public class  (commons-lang3 3.20.0)
static String abbreviate(String str, int maxWidth)
Abbreviates a String using ellipses. This will turn "Now is the time for all good men" into "Now is the time for..."

Specifically:

- If the number of characters in `str` is less than or equal to `maxWidth`, return `str`.
- Else abbreviate it to `(substring(str, 0, max-3) + "...")`.
- If `maxWidth` is less than `4`, throw an IllegalArgumentException.
- In no case will it return a String of length greater than `maxWidth`.

StringUtils.abbreviate(null, *)      = null
StringUtils.abbreviate("", 4)        = ""
StringUtils.abbreviate("abcdefg", 6) = "abc..."
StringUtils.abbreviate("abcdefg", 7) = "abcdefg"
StringUtils.abbreviate("abcdefg", 8) = "abcdefg"
StringUtils.abbreviate("abcdefg", 4) = "a..."
StringUtils.abbreviate("abcdefg", 3) = IllegalArgumentException

@param str the String to check, may be null.
@param maxWidth maximum length of result String, must be at least 4.
@return abbreviated String, `null` if null String input.
@throws IllegalArgumentException if the width is too small.
@since 2.0
```

```
$ jdoc class org.apache.commons.lang3.tuple.Pair
org.apache.commons.lang3.tuple.Pair<L, R>  public abstract class  (commons-lang3 3.20.0)
implements java.util.Map.Entry, java.lang.Comparable, java.io.Serializable
A pair consisting of two elements.

static final Pair<?, ?>[] EMPTY_ARRAY         An empty array.
Pair()                                        Constructs a new instance.
static <L, R> Pair<L, R>[] emptyArray()       Returns the empty array singleton that can be assigned without compiler warning.
static <L, R> Pair<L, R> of(L left, R right)  Creates an immutable pair of two objects inferring the generic types.
static <L, R> Pair<L, R> of(Map.Entry<L, R> pair)  Creates an immutable pair from a map entry.
static <L, R> Pair<L, R> ofNonNull(L left, R right)  Creates an immutable pair of two non-null objects inferring the generic types.
<E extends Throwable> void accept(FailableBiConsumer<L, R, E> consumer) throws E  Accepts this key and value as arguments to the given consumer.
<V, E extends Throwable> V apply(FailableBiFunction<L, R, V, E> function) throws E  Applies this key and value as arguments to the given function.
int compareTo(Pair<L, R> other)               Compares the pair based on the left element followed by the right element.
boolean equals(Object obj)                    Compares this pair to another based on the two elements.
L getKey()                                    Gets the key from this pair.
abstract L getLeft()                          Gets the left element from this pair.
abstract R getRight()                         Gets the right element from this pair.
R getValue()                                  Gets the value from this pair.
int hashCode()                                Returns a suitable hash code.
String toString()                             Returns a String representation of this pair using the format `(left,right)`.
String toString(String format)                Formats the receiver using the given format.
inherited from Map.Entry: setValue
inherited from Object: getClass, clone, notify, notifyAll, wait, finalize
```

A simple name that matches more than one type exits with code `2` and lists the candidates:

```
$ jdoc class StringUtils
2 types named StringUtils on this classpath:
  org.apache.commons.codec.binary.StringUtils  commons-codec 1.19.0
  org.apache.commons.lang3.StringUtils         commons-lang3 3.20.0
Re-run with a fully qualified name.
```

The header line always names the artifact and version. When a type has no sources jar, the header says where the
answer came from: `[from javadoc jar]`, or `[decompiled, no docs]` when only signatures are available.

## Requirements

- Linux (x86_64 or aarch64).
- Java 25 as the `java` on `PATH`. The JDK's `lib/src.zip` provides the JDK documentation.
- Maven (`mvn`) and a local repository (`~/.m2/repository`) that already holds the module's dependencies. jdoc runs
  `mvn -o dependency:list` and never goes online. Put `-sources.jar` files (or `-javadoc.jar` files) in the
  repository if you want docs, not only signatures.

## Install

Releases are on the [releases page](https://github.com/vecluu/jdoc/releases); the latest is `0.1.0`, and
[`CHANGELOG.md`](CHANGELOG.md) lists what each one contains. Each release has `jdoc-<version>.tar.gz`, a
`SHA256SUMS` file and signed build provenance.

```sh
# online: download both files, then check that GitHub Actions built the tarball from this repository
gh release download v<version> -R vecluu/jdoc     # or download them from the releases page
gh attestation verify jdoc-<version>.tar.gz -R vecluu/jdoc

# copy both files to the target machine (this can be an airgapped one), then:
sha256sum -c SHA256SUMS
tar xzf jdoc-<version>.tar.gz
sudo ./jdoc-<version>/install.sh --skills-dir <opencode skills dir>
```

`install.sh` options:

| Option | Default | Effect |
| --- | --- | --- |
| `--prefix DIR` | `/opt/jdoc` | Install root: `DIR/lib/jdoc.jar`, the extracted sqlite native library, the AOT cache, and the licence files in `DIR/share/doc/jdoc/` |
| `--bindir DIR` | `/usr/local/bin` | Where the `jdoc` launcher goes |
| `--skills-dir DIR` | none | Also install the opencode skill as `DIR/jdoc/SKILL.md` |
| `--no-aot` | off | Skip training the AOT cache; the launcher then uses a per-user fallback archive |

The installer runs offline. It checks Java 25, extracts the sqlite native library once, and trains a JDK AOT cache.
The training indexes the JDK and a small generated library, takes about 8 s and produces a cache of about 44 MB. If
you change the JDK or the jar, run the installer again. Until you do, the launcher falls back on its own to a slower
per-user archive.

After installing:

1. Paste [`AGENTS-snippet.md`](dist/AGENTS-snippet.md), which is also in the bundle, into each repository's `AGENTS.md`.
   It tells the agent that the skill exists.
2. Run `jdoc index` once in a real module. The first run indexes the JDK and the module's jars, which takes several
   seconds. It then lists the types that have no sources, so you can see which `-sources.jar` files are missing.

The skill ([`dist/skill/SKILL.md`](dist/skill/SKILL.md)) tells the agent when a lookup is worth a call. Use it for
unfamiliar libraries or versions, for unknown names, after a compile error on a library call, and when behaviour
matters (null handling, exceptions, thread safety). Common JDK calls don't need one. The reasoning is in
[`docs/decisions/skill-design.md`](docs/decisions/skill-design.md).

## Usage

```
jdoc <command> [<argument>] [--from <path>] [--page <n>]
```

| Command | Returns |
| --- | --- |
| `find <pattern>` | Types and members whose names match: substring or camel humps (HSR). |
| `class <type>` | Kind, supertypes, first doc paragraph, members with first sentences, inherited member names. |
| `method <type>#<name>[(<params>)]` | Full Javadoc of a method, or of every overload when (`<params>`) is omitted. |
| `source <type>#<name>[(<params>)]` | The member's implementation with its Javadoc. |
| `impls <type>` | Known subtypes and implementations on the classpath. |
| `member <name>` | Every type declaring a member with this name, as `Type#name(params)`. |
| `package <pkg>` | The package's doc, then its types with first sentences. |
| `index [--force]` | Resolve and index the current module; report types without sources. |

| Option | Meaning |
| --- | --- |
| `--from <path>` | Pick the module from this file's location instead of the working directory. |
| `--page <n>` | Show page `<n>` (1 or more) of a capped result. |
| `--force` | `index` only: rebuild the module's artifacts from scratch. |
| `-h`, `--help` | Show the help; `jdoc <command> --help` for one command. |
| `-V`, `--version` | Print the version. |

- `<type>` is a fully qualified or simple name. Write nested types as `Outer.Inner` or `Outer$Inner`.
- `<params>` are simple type names, for example `'Map#of(Object,Object)'`. Quote the argument in the shell.
- Options go before or after the argument, as `--page 2` or `--page=2`. `--` ends the options.
- Output is capped at about 2,000 tokens per call. A capped result ends with a line such as
  `page 1/2 · next: jdoc class java.lang.String --page 2`.
- **Exit codes:** `0` found, `1` not found, `2` ambiguous (candidates printed), `3` error.

Environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `JDOC_CACHE` | `~/.cache/jdoc` | Index and cache directory |
| `JDOC_M2_REPO` | `~/.m2/repository` | Local Maven repository |
| `JDOC_MVN` | `mvn` on `PATH` | Maven executable |
| `JAVA_HOME` | the running JVM | JDK whose `lib/src.zip` is indexed |
| `JDOC_JAVA_OPTS` | none | Extra JVM options (launcher only) |
| `JDOC_WRAPPER_DEBUG` | unset | `1` prints the launcher's cache mode on stderr |

## How it works

- **Resolution:** jdoc finds the nearest `pom.xml` and runs `mvn -o dependency:list` there, including test scope,
  so Maven handles parents, BOMs and `dependencyManagement`. The result is cached under a hash of the module's POMs.
  If Maven fails, jdoc uses the last good list, or the newest local version marked `[version unresolved]`.
- **Index:** a SQLite index stores types, members, rendered signatures and rendered Javadoc. jdoc indexes each
  artifact lazily, the first time a module needs it, and indexes the JDK's `src.zip` once. A warm call is an index
  lookup and nothing else.
- **Doc source per type:** jdoc uses the sources jar (parsed with the JDK's javac Tree API) if it has the type,
  then the javadoc jar (HTML), then a Vineflower decompile. Each type takes the best source available, so one
  artifact can mix all three.
- **Cache:** everything lives in `~/.cache/jdoc` (or `$JDOC_CACHE`). jdoc writes nothing into your projects.
- **Refresh:** every call checks the module's POMs and the jars it uses. A changed POM triggers resolution again,
  and a new or changed jar is indexed again on its own. `jdoc index --force` rebuilds the module's artifacts.

[`docs/SPEC.md`](docs/SPEC.md) has the full design and [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) has the code
structure.

## Performance

A JVM start dominates every call. The installer's AOT cache and launcher flags bring a warm call (index already
built) under 100 ms. The table shows medians from
[`docs/decisions/startup.md`](docs/decisions/startup.md), measured with `scripts/bench-startup.sh` on a Ryzen 5 3600
with JDK 25. Both columns use the launcher's JVM flags.

| Command | No cache | AOT cache |
| --- | --- | --- |
| `--version` | 38 ms | 55 ms |
| `class java.util.Optional` | 169 ms | 97 ms |
| `method java.util.Optional#orElseThrow` | 153 ms | 88 ms |
| `find HashMap` | 185 ms | 116 ms |
| `member orElseThrow` | 148 ms | 89 ms |
| `source java.util.Optional#orElseThrow` | 161 ms | 95 ms |
| First call after adding a dependency with a sources jar (`class`) | 422 ms | 154 ms |
| First call after adding a dependency with only a binary jar (`source`, decompiled) | 789 ms | 477 ms |

If the AOT cache no longer matches the jar or JDK, the launcher's fallback archive answers in about 130 ms. Plain
`java -jar jdoc.jar` takes about 220 ms. The first call in a module also runs Maven, which takes 1.5 to 2 s, and
indexing the JDK takes a few seconds once per cache.

## Building from source

You need JDK 25 to build. The Maven wrapper downloads Maven itself.

```sh
./mvnw verify                         # unit tests and integration tests, builds target/jdoc.jar
java -jar target/jdoc.jar --help
```

Release steps, as CI runs them:

```sh
export SOURCE_DATE_EPOCH=$(git log -1 --format=%ct)            # commit time: timestamps in the jar and the tarball
./mvnw -B -Drevision=0.1.0 verify                              # the version printed by `jdoc --version`
scripts/smoke-test.sh target/jdoc.jar it/sample-project         # offline end-to-end check (see below)
scripts/assemble.sh 0.1.0                                       # out/jdoc-0.1.0.tar.gz and out/SHA256SUMS
scripts/install-test.sh out/jdoc-0.1.0.tar.gz it/sample-project # installs into temp dirs and runs the smoke test
scripts/bench-startup.sh target/jdoc.jar                        # startup medians per command
```

The build is reproducible: the same commit, version and `SOURCE_DATE_EPOCH` give a byte-identical `jdoc.jar` and
tarball, as long as the JDK compresses the same way. CI uses Temurin 25 on `ubuntu-24.04`. A JDK linked against a
different zlib (for example the system zlib-ng on Fedora) gives the same entries with different compressed bytes.
Without `SOURCE_DATE_EPOCH`, the jar gets a fixed timestamp from `pom.xml`.

The smoke test and the install test need `mvn` on `PATH` (or `JDOC_MVN`) and network access once. They download
the sample project's dependencies into a temporary local repository, then run every command offline with a
temporary `JDOC_CACHE`. They never touch `~/.m2` or `~/.cache/jdoc`.

CI ([`.github/workflows/build.yml`](.github/workflows/build.yml)) runs on every pull request and every push to
`main`. It runs shellcheck, `verify`, the bundle assembly and the install test, with the launcher also run under
`dash`. A `v*` tag publishes a GitHub release with the tarball, `SHA256SUMS`, build provenance and the version's
`CHANGELOG.md` section as release notes. `gh workflow run build.yml --ref <branch> -f release-dry-run=<version>`
runs the same build and release job but publishes nothing.

## Contributing

Rules for contributors and coding agents (build, tests, commit format, how work is organised) are in
[`CLAUDE.md`](CLAUDE.md); the code structure is in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## License

jdoc is licensed under the [Apache License 2.0](LICENSE); see also [`NOTICE`](NOTICE). The jar bundles sqlite-jdbc,
jsoup, ASM, Vineflower (with its plugins) and SLF4J. [`THIRD-PARTY-NOTICES`](THIRD-PARTY-NOTICES) lists their licences.
