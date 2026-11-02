# Code map: build and delivery (`dist/`, `scripts/`, the build, `testsupport`)

Paths are relative to `src/main/java/io/github/vecluu/jdoc/` (tests: `src/test/java/io/github/vecluu/jdoc/`) unless they start
with `src/`. Binding rules, runtime flows, change recipes and the test map are in
[`docs/ARCHITECTURE.md`](../ARCHITECTURE.md); behaviour is in [`docs/SPEC.md`](../SPEC.md) (distribution and startup). Neither is repeated here.

## Purpose

Everything that turns the Java sources into a shipped, installed and fast-starting `jdoc`: the Maven build (shaded,
reproducible jar), the release bundle and its installer, the POSIX launcher with its AOT cache and CDS fallback, the
CI workflow and release path, the shell scripts that check all of it, and the shared test fixtures (`testsupport`).
Build commands are in `CLAUDE.md`, binding rules and the dependency list in `docs/ARCHITECTURE.md`, the release
procedure in `docs/RELEASING.md`; startup measurements and JVM cache pitfalls are in `docs/decisions/startup.md`;
licence reasoning is in `docs/decisions/licensing.md`.

## Key types

### Build files (no Java)

- `pom.xml` — single module `io.github.vecluu:jdoc`, version `${revision}` (default `0.0.0-SNAPSHOT`, CI passes
  `-Drevision=<version>`). `<build><directory>` is `${jdoc.buildDir}` (default `target`), `finalName` `jdoc`.
  - compiler: `release 25`, `-parameters`, `-Xlint:all,-processing,-serial`, Lombok as annotation processor.
  - surefire `*Test` / failsafe `*IT`, both with `--enable-native-access=ALL-UNNAMED` (sqlite). Failsafe runs ITs
    against `target/classes` (not the shaded jar) and passes system properties: `jdoc.projectRoot`, `jdoc.shadedJar`,
    `jdoc.runtimeDependencies` (`LicenseNoticesIT`), `jdoc.itFixtureJars` (`Jdk8JavadocParameterNamesIT`),
    `jdoc.mavenHome`, `jdoc.localRepository`, `jdoc.offlinePlugins` (`RealMavenResolutionIT`).
  - dependency plugin: `list` (package) writes `runtime-dependencies.txt`; `copy` (pre-integration-test) fetches an
    old commons-lang3 jar + javadoc jar (JDK 8 javadoc layout) into `it-fixture-jars/`.
  - shade (package) replaces `jdoc.jar` in place: drops signatures, `module-info`, dependency `META-INF/NOTICE*` and
    licences (Apache licence resource transformer), adds root `LICENSE`, `NOTICE`, `THIRD-PARTY-NOTICES` under
    `META-INF/`, manifest `Main-Class`, `Multi-Release`, `Enable-Native-Access: ALL-UNNAMED`,
    `Implementation-Version: ${project.version}`.
  - jar plugin `forceCreation=true`; resources plugin pinned (see Gotchas).
  - `project.build.outputTimestamp` fixed; profile `source-date-epoch` (active when env
    `SOURCE_DATE_EPOCH` is set) uses that value instead.
- `.mvn/wrapper/maven-wrapper.properties` — script-only wrapper (no wrapper jar) that pins the Maven version; `mvnw`
  at the root.
- `.mvn/jvm.config` — `--sun-misc-unsafe-memory-access=allow` for Maven's own JVM on JDK 25.
- `LICENSE` (Apache-2.0), `NOTICE` (jdoc + sqlite-jdbc attribution), `THIRD-PARTY-NOTICES` (one `[id]` section per
  bundled component, incl. the three Vineflower plugin jars' contents), `CHANGELOG.md` (Keep a Changelog; the
  release notes source).

### Delivery files (`dist/`, `scripts/`, `.github/`, `it/`)

- `dist/bin/jdoc` — POSIX `sh` launcher template; `@LIB@` and `@JVM_FLAGS@` are filled in by the installer. Chooses
  `-XX:AOTCache=lib/jdoc.aot` when the fingerprint in `lib/jdoc.aot.key` matches, else a per-user dynamic CDS archive
  `${JDOC_CACHE:-~/.cache/jdoc}/cds/jdoc.jsa` (`-XX:+AutoCreateSharedArchive`), else nothing. Env: `JDOC_JAVA_OPTS`,
  `JDOC_WRAPPER_DEBUG=1`, `JDOC_CACHE`.
- `dist/install.sh` — bash installer run from the extracted bundle: `--prefix` (`/opt/jdoc`), `--bindir`
  (`/usr/local/bin`), `--skills-dir`, `--no-aot`. Holds the JVM flags of every call (`jvm_flags`) and the two training
  workloads (`workload` for the JDK, `library_workload` with `--from ../library`).
- `dist/skill/SKILL.md`, `dist/AGENTS-snippet.md` — the opencode skill and the `AGENTS.md` snippet (source of truth;
  rationale in `docs/decisions/skill-design.md`). Shipped in the bundle, installed only with `--skills-dir`.
- `scripts/assemble.sh <version>` — bundle `out/jdoc-<version>.tar.gz` + `out/SHA256SUMS` from `JDOC_JAR`
  (default `target/jdoc.jar`).
- `scripts/install-test.sh <tarball> [project]` — installs the tarball the way a user does and checks it.
- `scripts/smoke-test.sh <jar|launcher> <project>` — offline end-to-end check against `it/sample-project`.
- `scripts/release-notes.sh <version>` — prints the `CHANGELOG.md` section plus an install/verify footer.
- `scripts/bench-startup.sh <jar>` — median wall time per command, incl. "first call after adding a dependency".
- `.github/workflows/build.yml` — jobs `build` and `release` (see Flows).
- `it/sample-project/` — one Maven module: commons-lang3 (sources jar), commons-codec (javadoc jar only), jspecify
  (binary only, decompiled); dependency plugin pinned; `Greeter.java` is the `--from` target.

### Java: AOT training and version (package `cli`; the rest of `cli` is in `lookup-and-cli.md`)

- `cli.TrainingWorkload` — runs a workload file (one command per line, `#` comments) in one JVM, a fresh `JdocCli`
  per command; exit 3 only if some command exited 3. Reached via system property `jdoc.aotTraining`
  (`TrainingWorkload.PROPERTY`), checked first in `Main.run`; not in the parser or help.
- `cli.TrainingLibrary` — has its own `main <repository> <dependency-list>`; builds a throwaway library with the JDK's
  compiler and javadoc tool in three `TrainingLibrary.Flavour`s (`SOURCES`, `JAVADOC`, `BINARY`) as
  `jdoc.training:training-<flavour>:1.0` in a Maven layout, plus the `dependency:list` text (`dependencyList`).
- `cli.TrainingLibrarySources` — the library's Java sources as text blocks, one copy per flavour package.
- `cli.ManifestVersion` — `jdoc --version` = manifest `Implementation-Version` (`development build` outside the jar).

### Package `testsupport` (`src/test/java/.../testsupport`)

- `TestLibrary` (+ `TestLibrary.Builder`) — compiles in-memory sources with the JDK compiler and writes binary,
  sources and real javadoc jars; options to omit files from the sources jar, add non-Java sources, toggle `-parameters`.
- `BuiltLibrary` — record of the jars `TestLibrary.writeTo` produced.
- `SampleLibrary` — the shared `com.example:sample-lib:1.0.0` (sources under
  `src/test/resources/io/github/vecluu/jdoc/testsupport/sample-lib/`), `TypeName` constants, presets `complete()`,
  `mixed()` (per-type doc-source fallback: javadoc-only `GeneratedModel`, `.kt`-only `KotlinUtils`), `binaryOnly()`.
- `FakeMavenRepo` — repository layout in a temp dir: `install(library[, scope])` → `ResolvedDependency`, `writePom`,
  `depsTxt`/`depsTxtLine` (the `mvn dependency:list` format `module.DepsFileParser` reads).
- `TestEnvironments` — `JdocEnvironment` rooted in a temp dir (`cache`, `m2/repository`, `bin/mvn`, `work`) with the
  running JDK as Java home; `installFakeMaven(env, scriptBody)`.
- `JarWriter` — deterministic jars (sorted entries, directory entries, fixed `ENTRY_TIME`).
- `Prerequisites` — `require(met, what)`: skips the test locally, fails it when `CI=true`.

## Collaborations

- The build packages all main packages into one jar; the only runtime coupling of this area to the Java code is
  `Main` → `cli.TrainingWorkload` (training mode), `cli.ManifestVersion` (version), and `TrainingLibrary.main`
  (called by the installer via `java -cp jdoc.jar`).
- The installer and launcher drive jdoc only through its public surface plus env vars read by
  `config.JdocEnvironment`: `JDOC_CACHE`, `JDOC_M2_REPO`, `JDOC_MVN`, `JAVA_HOME`. Training and all scripts use a fake
  `mvn` that copies a `deps.list` from the module directory to `-DoutputFile=` (jdoc's `module` package calls
  `mvn -o dependency:list`).
- The installer also calls sqlite-jdbc's OSInfo class (`java -cp jdoc.jar org.sqlite.util.OSInfo`) to name the native
  library folder inside the jar.
- `testsupport` is used by the tests of almost every package; it depends on `model` (`Gav`,
  `ResolvedDependency`, `Scope`, `TypeName`) and `config.JdocEnvironment`.
- ITs that exercise this area from Java live in other packages (`cli`, root, `module`, `indexer`), see Tests.

## Flows

**Build** (`./mvnw -B [-Djdoc.buildDir=…] [-Drevision=…] verify`): compile → surefire → `package`: jar plugin
(forced), shade (in place), dependency `list` → `pre-integration-test`: copy IT fixture jars → failsafe ITs (several
spawn the shaded jar, located as `<buildDir>/jdoc.jar`, the parent of `test-classes`) → verify.

**Version**: `-Drevision` → manifest `Implementation-Version` → `ManifestVersion.text()` → `jdoc --version`;
`assemble.sh` checks it equals the requested version and writes `VERSION`; `install-test.sh` checks the installed
launcher prints it.

**Assemble** (`scripts/assemble.sh <version>`): check jar version → stage `jdoc-<version>/` (`VERSION`, `install.sh`,
`AGENTS-snippet.md`, `LICENSE`, `NOTICE`, `THIRD-PARTY-NOTICES`, `bin/jdoc`, `lib/jdoc.jar`, `skill/SKILL.md`) with
explicit modes → `tar --sort=name --format=gnu --mtime=@$SOURCE_DATE_EPOCH --owner=0 --group=0` | `gzip -9n` →
`SHA256SUMS`. `SOURCE_DATE_EPOCH` defaults to the last commit time. Nothing machine-specific goes in.

**Install** (`dist/install.sh`): Java 25 check on the PATH `java` → drop old `jdoc.aot`/key → copy jar (temp + mv),
`VERSION`, licence files to `<prefix>/share/doc/jdoc/` → extract `libsqlitejdbc.so` into `lib/native/` (`unzip -p`,
else the JDK's `jar`) → render launcher into bindir → AOT training (below) → write `lib/jdoc.aot.key` → skill.

**Install-time AOT training**: in a `mktemp -d` work dir: throwaway module `module/` (no deps), fake `bin/mvn`, temp
`m2`/`cache`. (1) `java $jvm_flags -cp jdoc.jar …cli.TrainingLibrary m2 library/deps.list` in its own JVM (keeps
javac/javadoc out of the recording); on success add `library/pom.xml` and append `library_workload`; on failure one
warning line with the reason, JDK-only training. (2) `train_cache`: `-XX:AOTMode=record -XX:AOTConfiguration=…
-Djdoc.aotTraining=workload.txt -jar jdoc.jar` (→ `Main.run` → `TrainingWorkload.runFile`: indexes the JDK, then the
three training jars incl. a Vineflower decompile), then `-XX:AOTMode=create -XX:AOTCache=lib/jdoc.aot.tmp`.
(3) If it fails, retry once with `-XX:+UnlockDiagnosticVMOptions -XX:-AOTRecordTraining` (one stdout line). (4) On
success write the key (`aot_flags`, `aot_home`, `aot_stat` = `stat -L -c '%s %Y %i'` of jar, PATH `java`, the JDK's
`release` and `lib/modules`) and rename both into place; on double failure warn with reason + log tail, exit 0.

**Launcher start** (`bin/jdoc`): find `java` on PATH (exit 127 if none) → prepend `-jar lib/jdoc.jar` and the sqlite
`-Dorg.sqlite.lib.path/name` props → source `jdoc.aot.key`; flags equal and `stat` line equal → `-XX:AOTCache`;
else per-user `cds/jdoc.jsa` (created dir; a file without the dynamic-archive magic is deleted first) with
`-XX:-PrintWarnings`; else no cache → `exec java $jvm_flags $log_flags $JDOC_JAVA_OPTS "$@"`. Log flags send JVM
warnings to stderr and silence `aot`/`cds` messages.

**CI** (`build.yml`, `build` job, every PR and push to `main`): checkout → Temurin 25 → install shellcheck/dash if
missing → version step (dispatch input `release-dry-run` → that version; else tag `v*` → tag version; else
`0.0.0-<sha7>`; on tag/dry run `release-notes.sh` fails fast if the CHANGELOG section is missing; exports
`SOURCE_DATE_EPOCH` = commit time) → `./mvnw -B -ntp -Drevision=$VERSION verify` (with `CI=true`, so missing IT
prerequisites fail) → `assemble.sh` → `install-test.sh out/jdoc-$VERSION.tar.gz it/sample-project` with
`INSTALL_TEST_REQUIRE_DASH=1` (which runs `smoke-test.sh` through the installed launcher) → upload `out/`.

**Release** (`release` job, push of a `v*` tag or dispatch with `release-dry-run`): download artifact `jdoc-*` →
check `out/` holds exactly the tarball and `SHA256SUMS`, `sha256sum -c` → notes from `release-notes.sh` → (publish
only, i.e. a push of a `v*` tag; a dispatch never publishes) attest the tarball (SLSA provenance) →
`gh release create v$VERSION … --verify-tag --notes-file …` (`--prerelease` if the version has `-`; tag must equal
`v$VERSION`); a dry run prints the command instead. The release job is the only job
with write permissions. Tagging steps and how to verify a release: `docs/RELEASING.md`.

## Tests

All under `src/test/java/io/github/vecluu/jdoc/`. ITs that need the fat jar run after `package` (failsafe).

- `LicenseNoticesIT` (root package) — root licence files equal the jar's `META-INF/` copies byte for byte; no other
  licence/notice at the `META-INF/` root; every jar entry (and nested-jar entry) maps through the `OWNERS`
  prefix allow-list to a `THIRD-PARTY-NOTICES` section; every runtime `g:a:v` is named there. Fails on any new
  dependency, version bump or new jar content until the notices are reviewed. Skips only without
  `jdoc.projectRoot` (IDE run).
- `cli.InstallerIT` (slow: three real installs with AOT training, Linux only) — installs a bundle layout built from
  `dist/` + fat jar into a prefix with a space: AOT cache used (also for the first calls after adding a javadoc-only
  and a binary-only library, checked with `-Xlog:class+load`), nothing extracted to `java.io.tmpdir`, silent fallback
  after the jar changes, empty archive rebuilt; generator failure → one warning line; simulated JVM crash in
  `AOTMode=create` (fake JDK on PATH) → one retry; `scriptsAreShellcheckClean` runs shellcheck on `dist/install.sh`,
  `dist/bin/jdoc`, `scripts/*.sh` (this replaced a separate CI step).
- `cli.BenchStartupScriptIT` — runs `scripts/bench-startup.sh` with one run per row; expects every command row plus
  the three `new-dep` rows.
- `cli.HotPathClassLoadingIT` and `cli.EndToEndIT` also spawn the shaded jar; they are described in
  `lookup-and-cli.md`.
- `cli.TrainingWorkloadTest`, `cli.TrainingLibraryTest`, `cli.TrainingLibrarySourcesTest` (unit),
  `cli.TrainingLibraryIT` (three flavours, jar contents, POMs, scratch dir removed).
- `testsupport.LibraryFixturesIT` (fixture jars, fake repo, fake mvn script), `testsupport.PrerequisitesTest`.
- Not part of `verify`: `scripts/smoke-test.sh` and `scripts/install-test.sh` (run by CI after `verify`; they need
  network once to prime a private repository) and `scripts/bench-startup.sh` measurements.

## Extension points

- **Bundle contents**: change `assemble.sh` (staging), the `expected` entry list in `install-test.sh`, the
  release-files check in `build.yml` (only for release assets), and `InstallerIT`'s `bundle()` together.
- **JVM flags of every call**: `jvm_flags` in `dist/install.sh` only (baked into the launcher and the key; a change
  invalidates old caches by design). `bench-startup.sh` has its own `BENCH_JVM_FLAGS` default, which is not the
  launcher's.
- **What the AOT cache covers**: the `workload`/`library_workload` lists in `install.sh`; library shapes in
  `TrainingLibrarySources`; flavours in `TrainingLibrary.Flavour`. Measure with `bench-startup.sh` before/after.
- **New runtime dependency**: `pom.xml` + a `THIRD-PARTY-NOTICES` section + `LicenseNoticesIT.OWNERS` entries
  (the IT forces this); the dependency list is fixed (`docs/ARCHITECTURE.md`).
- **New IT needing an external file**: let the build provide it (failsafe system property, dependency-plugin
  execution) rather than reading `~/.m2`; guard it with `Prerequisites` (rule in `docs/ARCHITECTURE.md` "Tests").
- **New smoke scenario**: add a dependency flavour to `it/sample-project/pom.xml` and its priming/assertions to
  `smoke-test.sh`.

## Gotchas

- **Reproducibility**: the jar is reproducible from `SOURCE_DATE_EPOCH` (or the fixed default) and the tarball from
  the jar, but only with the same toolchain: a JDK whose `libzip` uses the system zlib produces different deflate
  bytes than Temurin. Independent rebuilds must use the CI toolchain.
- **Shade in place**: without `forceCreation=true` an incremental build treats the shaded `jdoc.jar` as up to date and
  re-shades it ("duplicate entry: META-INF/THIRD-PARTY-NOTICES").
- **Plugin pins exist for offline ITs**: `maven-resources-plugin` is pinned so `RealMavenResolutionIT`'s offline
  temp project can pin the same versions (via `jdoc.offlinePlugins`); bumping compiler/resources/dependency plugin
  versions changes what that IT pins. `it/sample-project` pins the dependency plugin for the same reason.
- **The Vineflower plugin jars stay**: removing `META-INF/plugins/**` changes decompiler output; decompiler unit tests
  use the unshaded jar and would not notice.
- **AOT cache validity**: a rejected AOT cache makes the JVM run without any CDS, hence the fingerprint check in sh
  (one `stat` fork) instead of letting the JVM decide. `stat -L -c` is GNU/busybox; elsewhere the launcher always
  falls back (safe). Re-running `install.sh` is the only way to rebuild the AOT cache; the fallback archive holds only
  the classes of the call that created it.
- **JDK 25 crash during `AOTMode=create`** (rare SIGSEGV in training-data handling): the installer retries once with
  `-XX:-AOTRecordTraining`; that cache is slightly slower per lookup. Keep the retry even on a fixed JDK.
- **Training must stay offline and isolated**: temp `JDOC_CACHE`, `JDOC_M2_REPO`, fake `mvn`, `JAVA_HOME` = the PATH
  java's home. The record run must not load javac/javadoc for generation, which is why `TrainingLibrary` runs in its
  own JVM first. `AOTMode=create` does not run the application.
- **Launcher is `sh`, installer is `bash`**: `bin/jdoc` must stay POSIX (tested under dash); JVM args are prepended
  with `set --` so user arguments with spaces pass through. Do not print anything on stdout from the launcher.
- **install.sh `usage()`** prints a fixed line range of the file with `sed -n`: editing the header comment shifts it.
- **install-test.sh fails on any `warning`** in the installer output and on any unexpected tar entry; CI also fails
  when dash is missing. A generator or training fallback therefore fails CI even though users get a working install.
- **Fake `mvn` contract**: it copies `deps.list` from the current directory; jdoc must run Maven in the module
  directory. The same snippet is duplicated in `install.sh`, `bench-startup.sh` and `InstallerIT`.
- **Fat-jar location in ITs**: `fatJar()` is duplicated in `InstallerIT`, `BenchStartupScriptIT`,
  `HotPathClassLoadingIT` and `EndToEndIT` (`<parent of test-classes>/jdoc.jar`), so it honours `-Djdoc.buildDir`.
- **Smoke-test facts**: `class StringUtils` is ambiguous (lang3 and codec) and asserted as exit 2; the smoke test
  checks jdoc wrote nothing into the project (`target/` must not appear).
- **Release workflow**: only a `push` of a `v*` tag publishes (`PUBLISH` checks the event and the ref); the version
  step prefers the `release-dry-run` input over a tag, so a dispatch with it is a dry run on any ref, a tag included.
  The CHANGELOG section for the version must exist before tagging (checked before the build).
