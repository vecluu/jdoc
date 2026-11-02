# jdoc startup research

How to cut the cost of a warm `jdoc` call (index already built). Everything below was measured on this machine
unless it says "estimated". No file under `src/`, `pom.xml` or `tasks/` was changed; all experiments ran on copies
of `target/jdoc.jar` in a scratch directory.

## Summary

- A warm `jdoc method java.util.Optional#orElseThrow` takes **~590 ms** with the planned wrapper
  (`-XX:TieredStopAtLevel=1`), ~655 ms with plain `java -jar`.
- It is not "JVM startup" in the narrow sense: an empty JVM boots in 18 ms. The time is **cold code**: loading,
  verifying and interpreting ~2,900 classes that are not in the JDK's default CDS archive (javac 1,050, the fat jar
  740, java.base 450, java.xml 180), plus picocli's reflection and sqlite-jdbc copying its native library to `/tmp`
  on every call.
- **Best measured result without a daemon: 205 ms** (−65%): a JDK 25 AOT cache, `-XX:+UseSerialGC
  -XX:-UsePerfData -XX:TieredStopAtLevel=1`, the sqlite native library extracted once at install time, and picocli
  re-targeted to Java 8 bytecode so the AOT cache can hold it. Without the picocli rewrite: **250 ms**.
  This needs no Java code changes: only wrapper, installer and (for picocli) one build step.
- **Best measured result overall: 14 to 17 ms** per call with a persistent daemon on a Unix socket (`socat`
  client), or 44 ms with a Java client started from an AOT cache. Needs real code and lifecycle work.
- Recommended: do the AOT cache plus flags plus pre-extracted sqlite first (small, safe, installer-only). Then
  remove picocli from the hot path and stop running javac/jsoup per query. Keep the daemon as a later, optional step.

## Setup

- AMD Ryzen 5 3600 (6 cores, 12 threads), 16 GB, Fedora (kernel 7.2), files in tmpfs and page cache.
- JDK: Microsoft Build of OpenJDK `25.0.5.0.2+1-LTS` on `PATH`.
- `target/jdoc.jar`: 15.8 MB, 1,734 entries (21 MB uncompressed are sqlite native libraries for 12 platforms).
- Warm call: temp module with the minimal `pom.xml`, fake `mvn` writing an empty dependency list,
  `JDOC_CACHE` and `JDOC_MVN` exported, index built by a first call (6.0 s). Every number is the **median of
  15 to 30 runs** after 2 warm-up runs, stdout and stderr to `/dev/null` (Python `subprocess` timer). Run-to-run
  noise is about ±10 ms.

## Baseline and time breakdown

| Command | Median |
| --- | --- |
| `java -version` | 17 ms |
| trivial `Hello` main (with or without `jdoc.jar` on the classpath) | 18 ms |
| `java -jar jdoc.jar --help` | 211 ms |
| `java -XX:TieredStopAtLevel=1 -jar jdoc.jar --version` | 173 ms |
| `java -jar jdoc.jar method java.util.Optional#orElseThrow` | 655 ms |
| same with `-XX:TieredStopAtLevel=1` (the planned wrapper) | 591 ms |

Jar size and the fat jar layout do not matter for startup: `Hello` costs the same 18 ms with the 15 MB jar on the
classpath (only the central directory is read).

### Where the 590 ms go

An in-process harness (`Split`, see "Experiments") warmed each subsystem separately before running the real query,
with `-XX:TieredStopAtLevel=1`. Three runs agreed within a few ms:

| Phase | No AOT cache | With AOT cache (picocli v52, sqlite pre-extracted) |
| --- | --- | --- |
| JVM boot (+ AOT preload of ~4,000 classes) | ~20 ms | ~45 ms |
| picocli + slf4j + `JdocCommand` setup (`--version` path) | 140 ms | 63 ms |
| sqlite-jdbc: load driver, extract native lib to `/tmp`, open | 108 ms | 18 ms (53 ms if still extracting) |
| JAXP DOM parse of `pom.xml` | 15 ms | 3 ms |
| javac (`JavacTool`, `DocTrees`) init + tiny parse | 130 ms | 16 ms |
| jsoup init + tiny fragment | 35 ms | 5 ms |
| the query itself (subsystems already loaded) | 155 ms | 75 ms |
| the same query a second time in the same JVM | 51 ms | 43 ms |
| **Total** | **~600 ms** | **~210 ms** |

Same JVM, 30 back-to-back calls of `Main.run` (no AOT, default JIT): 624 ms, 59, 52, 41, 37 … 28 (call 8),
25 (call 18), 17 ms (call 28). That is the floor a daemon reaches.

Other observations:

- **Class loading** (`-Xlog:class+load`): 4,252 classes, 1,355 from the default CDS archive, 2,897 parsed from
  jrt/jar: `jdk.compiler` 995, `jdoc.jar` 740 (jdoc 377, jsoup ~250, sqlite ~80, picocli ~175, slf4j ~25),
  `java.base` 448, `java.xml` 182, `java.compiler` 58, `java.sql` 21, 93 lambda/hidden classes.
  Vineflower and ASM classes do not load on this path (1 ASM interface only), so they cost nothing at runtime.
- **The query path runs javac and jsoup every time.** `method` re-parses `Optional.java` from `src.zip` with the
  javac Tree API and renders Javadoc HTML with jsoup. That is ~1,300 classes and most of the "query" time.
- **sqlite-jdbc extracts its native library on every call.** `SQLiteJDBCLoader` copies the 1.1 MB
  `libsqlitejdbc.so` to `java.io.tmpdir` as `sqlite-3.54.1.0-<random UUID>-libsqlitejdbc.so` (+ `.lck`), loads it
  and deletes it at exit. Generating the UUID initialises `SecureRandom` and the security providers, and the
  loader computes an MD5 digest. Verified by watching a private `-Dorg.sqlite.tmpdir`: the file appears during the
  call and is gone after. Setting `-Dorg.sqlite.lib.path=<dir> -Dorg.sqlite.lib.name=libsqlitejdbc.so` to a copy
  extracted once makes it load directly: **−55 ms without AOT, −63 ms with AOT**.
- **picocli 4.7.7 is Java 5 bytecode (class file version 49).** Even a one-option picocli command costs 85 to
  95 ms cold. Old class files are verified by the slow type-inferencing verifier and **cannot be stored in an AOT
  cache**: the cache build prints `Skipping picocli/...: Old class has been linked` (or `Verified with old verifier`
  for version 50), so picocli is loaded and verified from the jar on every call even with a cache.
- **slf4j-simple** init: 24 ms cold, 6 ms with AOT.
- Bytecode verification overall: `-XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote` gives 507 ms
  (−85 ms). Diagnostic flag, unsafe, not recommended; the AOT cache gets the same benefit legitimately because
  classes are verified when the cache is built.
- A JFR profile (1 ms sampling) of the no-AOT call agreed: picocli, sqlite open, javac parse and jsoup dominate;
  jdoc's own code is about 2%.

## JVM flags

All with `-jar jdoc.jar method java.util.Optional#orElseThrow`, no AOT cache unless stated.

| Flags | Median | vs. `TieredStopAtLevel=1` |
| --- | --- | --- |
| none | 656 ms | +65 |
| `-XX:TieredStopAtLevel=1` (wrapper today) | 591 ms | 0 |
| + `-XX:+UseSerialGC` | 580 ms | −11 |
| + `-XX:-UsePerfData` | 590 ms | −1 (also stops writing `/tmp/hsperfdata_<user>`) |
| + `-Xmx256m -Xms64m` | 592 ms | 0 |
| + `-Xshare:off` | 661 ms | +70 (shows what the default CDS archive is worth) |
| `-Xint` | 1,371 ms | +780 |
| + SerialGC + `-UsePerfData` + pre-extracted sqlite | 528 ms | −63 |
| with AOT cache: `TieredStopAtLevel=1` vs. full tiered (C2) | 205 vs. 243 ms | C1-only still wins |

`-Xss` and heap sizing do not matter for a call this short. `-Xverify:none`/`-noverify` are deprecated; see the
diagnostic flag above.

## Options

### 1. JDK 25 AOT cache (JEP 483, 514, 515) — recommended first

JDK 24 added ahead-of-time class loading and linking (JEP 483); JDK 25 added the one-step
`-XX:AOTCacheOutput` workflow (JEP 514) and method profiles in the cache (JEP 515). The cache holds the classes
of a training run already parsed, verified, loaded and linked; the JVM maps it at startup.

Measured (warm `method` call, all with `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:-UsePerfData` and
pre-extracted sqlite unless stated):

| Variant | Median |
| --- | --- |
| no cache | 528 ms |
| classic dynamic AppCDS (`-XX:ArchiveClassesAtExit`, then `-XX:SharedArchiveFile`) | 325 ms |
| `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=…` (self-maintaining dynamic CDS, JDK 19+) | 327 ms |
| AOT cache, plain `-jar` with no other flags | 339 ms |
| AOT cache + `TieredStopAtLevel=1` only | 301 ms |
| **AOT cache, original jar** | **250 ms** |
| **AOT cache, picocli re-targeted to Java 8 bytecode** | **205 to 215 ms** |
| AOT cache, picocli v52, sqlite still extracted per call | 268 ms |
| + diagnostic `-XX:+AOTAdapterCaching -XX:+AOTStubCaching` | 252 ms (no gain) |

Other commands with the same cache (trained only on `method`; picocli v52; same flags):

| Command | No cache | AOT cache |
| --- | --- | --- |
| `class java.util.Optional` | 544 ms | 242 to 250 ms |
| `find HashMap` | 399 ms | 255 ms |
| `source java.util.Optional#orElseThrow` | 524 ms | 212 ms |
| `member orElseThrow` | 302 ms | 156 ms |

A cache trained on a cold call (fresh `JDOC_CACHE`, so the JDK gets indexed during training) is 39 MB instead of
34 MB and performs the same for queries (216 ms), so one training run covers both indexing and lookups.
A cache trained with default flags (G1, full tiered) still works with SerialGC and C1-only at run time
(`-XX:AOTMode=on` accepted it).

**Constraints (verified here unless noted):**

- **Same jar path and same jar mtime.** After `touch jdoc.jar`: `This file is not the one used while building the
  AOT cache ... timestamp has changed`, `shared class paths mismatch`. A copy at another path with preserved mtime
  is also rejected (`Required classpath entry does not exist`). The cache must be built at the final install path.
- **Same JDK build** (JEP 483: same release, OS and architecture; in practice the exact build that created it).
  Upgrading the JDK on `PATH` invalidates the cache.
- **Classpath entries must be jars, not directories** (`Cannot have non-empty directory in paths`).
- **A rejected cache is worse than none:** the JVM falls back to *no* CDS at all, including the JDK's default
  archive: 599 ms vs. 524 ms without any cache, and it prints 3 to 4 `[error][aot]` lines on stderr. Add
  `-Xlog:aot=off -Xlog:cds=off` to keep stderr clean, and have the wrapper detect staleness (below).
  `-XX:AOTMode=on` instead turns a stale cache into a hard failure (`Unable to use AOT cache`, exit 1).
- **The one-step `-XX:AOTCacheOutput` sets `JAVA_TOOL_OPTIONS` for a child JVM** (`Picked up
  JAVA_TOOL_OPTIONS ...` on stderr). Any JVM the training run spawns (a real `mvn`!) inherits it. Train with the
  fake `mvn` (as in these experiments) or use the two-step workflow, which also worked (211 ms):
  `-XX:AOTMode=record -XX:AOTConfiguration=jdoc.aotconf`, then
  `-XX:AOTMode=create -XX:AOTConfiguration=jdoc.aotconf -XX:AOTCache=jdoc.aot`.
- Training takes ~2.4 s on a warm index, ~8 s on a cold one. Cache file: 34 to 39 MB.
- Picocli is excluded from the cache because of its Java 5 class files (see breakdown).

**Install-time training inside the airgap** (no network needed; all inputs are in the tarball and the JDK):

1. Installer copies the jar to its final path, e.g. `/opt/jdoc/lib/jdoc.jar`, and never touches it again.
2. Extract the sqlite library once for the machine:
   `unzip -j -o jdoc.jar "org/sqlite/native/Linux/$(uname -m)/libsqlitejdbc.so" -d /opt/jdoc/lib/native`
   (use `Linux-Musl/...` on musl; `unzip` may be absent, `jar xf` or `java -cp` a tiny extractor also work).
3. Create a temp module (minimal `pom.xml`), a fake `mvn` script, a temp `JDOC_CACHE`, then run once with exactly
   the runtime flags plus `-XX:AOTCacheOutput=/opt/jdoc/lib/jdoc.aot`, e.g.
   `jdoc method java.util.Optional#orElseThrow`. Delete the temp dirs.
4. Write a fingerprint next to the cache: path of the `java` on `PATH` (resolved), its `release` file
   checksum, the jar mtime.

**Wrapper** (validated flags; `@LIB@` filled by the installer):

```sh
#!/bin/sh
lib=@LIB@
aot=
if [ -f "$lib/jdoc.aot" ] && [ "$(cat "$lib/jdoc.aot.key" 2>/dev/null)" = "$(<compute fingerprint>)" ]; then
  aot="-XX:AOTCache=$lib/jdoc.aot"
fi
exec java -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:-UsePerfData \
  -Xlog:aot=off -Xlog:cds=off $aot \
  -Dorg.sqlite.lib.path="$lib/native" -Dorg.sqlite.lib.name=libsqlitejdbc.so \
  -jar "$lib/jdoc.jar" "$@"
```

The fingerprint check must be cheap (no `java -version`: that alone is 17 ms); `readlink -f "$(command -v java)"`
plus the `release` file's mtime is enough. On mismatch the wrapper runs without the cache and could print a one-line
hint to rerun `install.sh --retrain` (or retrain itself into `~/.cache/jdoc/aot/` under `flock`). The cache can also
live per user in `~/.cache/jdoc/aot/<fingerprint>.aot`, which avoids root-owned files and survives JDK upgrades by
keeping one cache per JDK.

Concurrency: the cache is mapped read-only, any number of parallel calls can share it. Writing it happens only
during training.

**Picocli and the AOT cache.** Two ways to get the extra 40 to 45 ms:

- Build step (no Java source changes, measured 250 → 205-215 ms): rewrite the shaded `picocli/**` classes to class
  file version 52 with ASM `ClassWriter.COMPUTE_FRAMES` (228 classes; ASM is already a dependency). Must use
  `new ClassWriter(flags)` without the `ClassReader` argument, or frames are not recomputed (`VerifyError: Expecting a
  stackmap frame`). Can run as a small exec step after shade. Downside: ships modified third-party bytecode.
- Code change (estimated ~150-160 ms): drop picocli from the call path. Even inside an AOT cache, picocli's
  reflection over the 17 annotations of `JdocCommand` costs ~60 ms (`--version` alone: 111 ms with AOT). A small
  hand-written parser for eight subcommands and a few options removes it; `--help` text becomes a static
  resource. picocli could stay for `--help` only.

### 2. Stop doing javac and jsoup work per query — code change

Under AOT the query itself is the largest remaining part (~75 ms cold, 43 ms even when hot in one JVM). Most of it
is re-parsing the whole declaring type's source with javac and rendering HTML with jsoup on every lookup.
Storing the rendered member docs (or at least the doc-comment text) in `index.db` during indexing, or memoising
the first rendering, turns a `method` call into SQLite reads. Estimated saving 40-60 ms with AOT and 250+ ms
without it (javac and jsoup class loading disappear from the hot path). Costs index size and indexing time; the
`source` command still needs the source text but not javac if offsets are stored (they already are).

### 3. Persistent daemon on a Unix domain socket

Prototype (scratch only): a Java server that keeps one JVM, accepts on a `0600` Unix socket and calls
`Main.run(args, out, err)` per request. Measured round trip from the shell, warm `method` query:

| Client | Median |
| --- | --- |
| `socat - UNIX-CONNECT:$sock` | 17 ms (14 ms after 100 calls, JIT warm) |
| Python 3 `socket` client | 38 ms |
| Java client with its own AOT cache, C1, SerialGC | 44 ms |
| Java client without AOT | 55 ms |

Daemon RSS: ~395 MB with default heap (set `-Xmx256m`/`-XX:+UseSerialGC` to shrink).

Trade-offs:

- **Thin client.** POSIX `sh`/`bash` cannot open Unix sockets. `socat`/`nc -U`/`python3` are not guaranteed in
  the airgap. A Java client costs ~44 ms with AOT, which keeps most of the gain. A bash-only client would need TCP on
  127.0.0.1 via `/dev/tcp` plus a secret token file (`0600`) because any local user can connect to TCP. A native
  helper needs a C toolchain in CI (none on this machine).
- **Socket path limit.** `sun_path` is 108 bytes; the first attempt in a deep temp dir failed with
  `Unix domain path too long`. Use `$XDG_RUNTIME_DIR/jdoc.sock` (here `/run/user/1000`, already `0700`), falling
  back to `~/.cache/jdoc/` or `/tmp/jdoc-$UID/`.
- **Code changes.** The request must carry the working directory and the `JDOC_*`/`JAVA_HOME`/`HOME` variables;
  `JdocEnvironment.from(env, props)` already allows building one per request, but `Main` uses `fromSystem`. stdout
  and stderr need separate framing plus the exit code. `FileIndexLock` keeps its in-JVM guard per instance, so
  concurrent requests must share one lock instance (or be serialised), otherwise two threads in one JVM would hit
  `OverlappingFileLockException`; cross-process locking with CLI calls stays as it is.
- **Lifecycle.** Start on first call (client spawns `java ... --daemon` detached, waits for the socket), idle
  timeout (e.g. 15 to 30 min) to free memory, a pid/lock file to stop two daemons racing, stale socket cleanup.
  Multiple agents share one daemon per user, so requests must be thread-safe or queued.
- **Staleness.** After an upgrade the old daemon keeps running old code: the client must send the jar's version or
  mtime and the daemon exits and is restarted on mismatch. Same for a different `java` on `PATH`.
- **Failure modes.** A hung or crashed daemon must not break the agent: client falls back to the in-process path
  on connect error or timeout.

Worth it if 200 ms per call is still too slow after options 1 and 2. The best case (14-17 ms with `socat`, ~44 ms
with a Java client) is 4 to 12 times faster than option 1.

### 4. GraalVM native-image — not recommended now

Not installed here, not measured. Facts and reasoning:

- Typical native-image CLI startup is 10 to 30 ms (estimated), similar to the daemon, without a background process.
- **sqlite-jdbc** supports native-image out of the box since 3.40.1.0 (it bundles reachability metadata and the
  native library; it still extracts it to temp unless `-Dorg.sqlite.lib.exportPath` places it next to the binary).
- **picocli** supports native-image via its `picocli-codegen` annotation processor.
- **jdk.compiler (javac Tree API)** is not a supported/tested native-image target; javac uses service loading,
  reflection and large static state. It may build with enough metadata, but that is unverified. Vineflower and
  jsoup are plain Java and likely fine.
- **Blocker: `JrtBytecodeProducer`** opens `jrt:/` with `java.home` of the module's JDK. For a foreign Java home
  this loads that JDK's `jrt-fs.jar` in a new class loader, which native-image cannot do (no dynamic class
  loading). JDK bytecode indexing would need a different implementation (e.g. reading `lib/modules` via
  `jimage` or `jmods`).
- Build/airgap: needs GraalVM in CI (not in the airgap), one binary per OS/arch (Linux x64 and aarch64), long
  builds (minutes, several GB RAM), and testing the native binary separately. It would remove the need for Java on
  PATH to run jdoc, but jdoc still needs a JDK for `src.zip`.

### 5. CRaC (checkpoint/restore) — not viable for a generic install

CRaC is not in OpenJDK mainline. It ships only in vendor builds: Azul Zulu 17/21/25 bundles named `-crac-` on
Linux x64/ARM64 (with CRIU or Azul's Warp engine), and BellSoft Liberica CRaC builds. The product requires "Java 25
on PATH", i.e. any vendor; this machine has the Microsoft build (no `-XX:CRaCCheckpointTo`). CRIU-based restore
needs extra privileges (`CAP_CHECKPOINT_RESTORE`/`CAP_SYS_PTRACE`) and breaks in some containers. Restore of a
small heap is roughly 30-50 ms (estimated). Only worth it if the installer may also ship a CRaC JDK.

### 6. Smaller items

| Item | Result |
| --- | --- |
| `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=~/.cache/jdoc/jdoc.jsa` | 327 ms (−260). Zero install work, rebuilds itself when the jar or JDK changes. Good fallback when no AOT cache is usable. Concurrent first runs writing the same file: untested. |
| Fat jar vs. `Class-Path` manifest with separate jars | No startup effect (central directory read only, `Hello` 18 ms either way). |
| Drop non-Linux sqlite natives from the jar | −~10 MB tarball, no startup effect. |
| `jlink` trimmed runtime in the tarball | No startup gain by itself (default CDS is equivalent). Would pin the JDK build, so the AOT cache never goes stale on a JDK upgrade; +~50-70 MB tarball, and jdoc still needs the user's JDK for `src.zip`. |
| Leyden AOT code compilation (cached JIT code) | Not in JDK 25 (only the diagnostic adapter/stub caching, no gain measured). JEP 516 (AOT object caching with any GC) is JDK 26. Revisit with a later LTS. |
| Lazy wiring | `CompositionRoot` already defers the application for `--help`/`--version`; Vineflower and ASM do not load on the lookup path. Nothing more to gain here. |

## Ranked options

Savings are for the warm `method` call against today's 591 ms wrapper.

| Rank | Option | Result | Effort | Risk | Airgap / installer impact | Code changes |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | AOT cache + `-XX:+UseSerialGC -XX:-UsePerfData -Xlog:aot=off -Xlog:cds=off` + pre-extracted sqlite lib | **250 ms measured (−340)** | S | Low; stale cache falls back to ~600 ms, so add a fingerprint check | Training run in `install.sh` (~8 s, fake mvn, temp cache), +35-39 MB on disk, extract `libsqlitejdbc.so` for `uname -m` | None (wrapper + installer) |
| 2 | Picocli to v52 bytecode at build time (on top of 1) | **205-215 ms measured (−40)** | S | Low-medium (modified third-party bytecode) | None | Build step only |
| 3 | Replace picocli on the hot path (on top of 1) | ~150-160 ms estimated (−50 to −60 more) | M | Low | None | `cli` package |
| 4 | Persist rendered docs, no javac/jsoup per query | −40-60 ms with AOT, −250 ms without (estimated) | M | Medium (index format, doc fidelity) | Larger index | `indexer`, `lookup`, schema |
| 5 | Daemon on Unix socket | **14-17 ms (`socat`) / 44 ms (Java+AOT client) measured** | L | Medium-high (lifecycle, concurrency, staleness) | Needs a client: socat not guaranteed; Java client OK | `Main`, env per request, lock sharing, server, client |
| 6 | `AutoCreateSharedArchive` (alternative to 1) | 327 ms measured | XS | Low | None | None |
| 7 | GraalVM native-image | ~10-30 ms estimated | XL | High (javac, jrt-fs for foreign JDK) | GraalVM in CI, per-arch binaries | JDK indexing rewrite, metadata |
| 8 | CRaC | ~30-50 ms estimated | L | High | Requires a CRaC JDK vendor build, privileges | Checkpoint hooks |

## Recommendation

1. **First:** ship option 1 in the wrapper and installer (no Java changes): AOT training run in `install.sh`,
   pre-extracted sqlite library, flags as in the wrapper above, fingerprint check with fallback to
   `-XX:+AutoCreateSharedArchive` (option 6) when the cache is stale. Add the picocli re-target build step (option 2)
   for 590 → ~210 ms. Change `OUTLINE.md`'s note "measure first, it is optional": the measurement says it is worth
   ~2.8x.
2. **Second:** code changes that shrink the work per call: drop picocli from the hot path (option 3) and stop
   running javac/jsoup per lookup (option 4). Together with the AOT cache this should land near 110-150 ms
   (estimated).
3. **Only if still too slow:** the daemon (option 5) with a Java client from an AOT cache (~44 ms) and an idle
   timeout, falling back to in-process execution on any connection problem.

## Measured (after picocli removal and docs in the index)

Harness: `scripts/bench-startup.sh <jar>` (temp module with a fake `mvn`, temp `JDOC_CACHE`/`JDOC_M2_REPO`, one
`jdoc index` to warm, then per command 2 unmeasured and 11 measured runs, median). `BENCH_JVM_FLAGS` replaces the JVM
flags (for the AOT cache: add `-XX:AOTCache=...`), `BENCH_RUNS`, `BENCH_WARMUP`, `BENCH_COMMANDS`, `BENCH_JAVA`,
`BENCH_KEEP`. Same machine and JDK as above; no AOT cache. "Wrapper flags" = `-XX:TieredStopAtLevel=1
-XX:+UseSerialGC -XX:-UsePerfData` + pre-extracted sqlite library (`-Dorg.sqlite.lib.path/.name`), i.e. the planned
wrapper without the cache.

| Command | before, C1 only | after, C1 only | before, wrapper flags | after, wrapper flags |
| --- | --- | --- | --- | --- |
| `--version` | 40 ms | 40 ms | 35 ms | 37 ms |
| `class java.util.Optional` | 310 | 233 | 248 | 167 |
| `method java.util.Optional#orElseThrow` | 278 | 216 | 225 | 151 |
| `find HashMap` | 389 | 242 | 326 | 185 |
| `member orElseThrow` | 280 | 212 | 219 | 148 |
| `source java.util.Optional#orElseThrow` | 515 | 221 | 447 | 159 |

`member orElseThrow` from a module of a 30-module reactor, wrapper flags: 255 → 150 ms (every call parsed all 31
POMs before). Classes loaded by `method`: 2,362 → 2,162 (not from CDS: 1,029 → 829; JAXP 140 → 1); by `source`:
3,924 → 2,166 (javac 1,079 → 0).

What changed, each measured on its own (median `method`, wrapper flags unless noted):

| Change | Effect |
| --- | --- |
| `index.db` opened on a background thread while the module is resolved (`IndexDatabase.openInBackground`) | 280 → 225 ms (C1 only), 220 → 180 ms |
| POM analysis snapshot: POM key and reactor reused while every probed path keeps its stamp; XML parser created lazily | 177 → 159 ms (resolution 28 → 8 ms); larger with a reactor |
| `IndexDatabase` logger looked up lazily and the open started first in the composition root (slf4j init no longer delays the open thread) | 163 → 150 ms, 227 → 212 ms (C1 only) |
| `find`: type names pre-filtered in SQL by the pattern's subsequence (was: every type name in scope mapped in Java); `findByNames` uses the `members_name` index (`COLLATE NOCASE IN` + exact `IN`; was a scan of every member in scope, 12 ms → 0.3 ms) | `find HashMap` 319 → 242 ms (C1 only) |
| `class`: nested types by a binary-name range (`findNested`) instead of reading the whole package (`java.util`: 432 rows, 15 ms) | `class java.util.Optional` 240 → 227 ms (C1 only) |
| `source`: member cut by its stored offsets when they still point at it (no javac); parse only as fallback | 438 → 222 ms (C1 only) |

Tried and dropped: initialising slf4j on its own thread as soon as a command is known (−5 ms, within noise of the
AOT case); `-Dslf4j.provider=...` (−3 ms, prints an info line).

Where a warm `method` call still spends its time (wrapper flags, ~150 ms; `-Xlog:class+load` timestamps + JFR):

- JVM boot to `Main` ~23 ms; CLI parsing and wiring ~50 ms of mostly cold class loading, including slf4j-simple
  initialisation (~12 ms, triggered by the first `@Slf4j` class) and the first record `ObjectMethods` /
  method-handle bootstraps (~9 ms).
- Background thread (overlapped): `new SQLiteConfig()` ~25 ms (sqlite-jdbc's `FastDateFormat` loads CLDR locale
  data), native library ~20 ms (~95 ms when it is extracted to `/tmp` per call, i.e. without the wrapper's
  `-Dorg.sqlite.lib.path`: then this thread is the critical path).
- Resolution with a snapshot hit ~8 ms; indexer freshness check ~10 ms (first SQL statements, JDBC classes).
- Lookup and rendering ~40 ms, almost all first-use class loading; SQL itself is a few ms.

Most of the remainder is cold code, which is what the AOT cache (next section) removes.

## Measured (installed AOT cache, wrapper and installer)

`scripts/bench-startup.sh` on the jar installed by `dist/install.sh` (median of 11 after 2 warm-ups, machine shared
with other builds, so ±10 ms). All columns use `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:-UsePerfData` and the
pre-extracted sqlite library; "AOT" adds the installer's cache, "fallback" the wrapper's
`-XX:+AutoCreateSharedArchive` archive (created by the bench's warm-up `index` call). Cache trained on the
installer's workload (index, version, help, method, class, find, member, package, impls, source): 35 to 37 MB,
installer run ~8.5 s.

| Command | no cache | AOT cache | fallback archive |
| --- | --- | --- | --- |
| `--version` | 40 ms | 51 ms | 40 ms |
| `class java.util.Optional` | 183 | 92 | 136 |
| `method java.util.Optional#orElseThrow` | 167 | 86 | 132 |
| `find HashMap` | 198 | 111 | 175 |
| `member orElseThrow` | 167 | 86 | 120 |
| `source java.util.Optional#orElseThrow` | 161 | 91 | 130 |

The wrapper itself (one `command -v` subshell, one `stat`, sourcing the fingerprint) costs 3 to 4 ms over calling
`java` directly. `--version` is ~10 ms slower with the cache (mapping 36 MB) but is not a hot call.

Verified while building the wrapper (JDK 25.0.5):

- **The research's `-Xlog:aot=off -Xlog:cds=off` is not enough**: those select only the exact tag sets, and the
  dynamic archive's `[warning][cds,dynamic] Unable to use shared archive` (after the jar changes) still appears. All
  default unified-logging output also goes to **stdout**, i.e. into jdoc's answer. The wrapper uses
  `-Xlog:disable -Xlog:all=warning:stderr -Xlog:aot*=off,cds*=off:stderr`: other JVM warnings move to stderr,
  cache messages disappear.
- `-XX:SharedArchiveFile` in a **missing directory** is a hard VM failure (exit 1); the wrapper creates the
  directory and leaves the flag out if it cannot.
- A dynamic archive **without a valid header** (empty file, or zeroed header: the JVM writes the header last, so
  an interrupted or concurrent write leaves one) is never rebuilt: every later call runs without any CDS and prints
  `-XX:+AutoCreateSharedArchive is unsupported when base CDS archive is not loaded` (a VM warning, not unified
  logging). A truncated file with a valid header is rebuilt. The wrapper reads the 4-byte magic
  (`a8 ab 0b f0`) with `od` on the fallback path only and deletes a bad file; it also passes `-XX:-PrintWarnings`
  there. Eight concurrent first calls: all succeed with identical output and empty stderr.
- The fallback archive holds the classes of the call that created it and is not extended later (a first
  `--version` gives a 0.35 MB archive that helps little); it is rebuilt only when the jar or JDK changes.
- An AOT cache renamed after `-XX:AOTMode=create` still works, so the installer builds it under a temp name and
  moves it in place. `-Djdoc.aotTraining=...` at training time does not stop the cache matching runtime calls.
- Java's temp dir ignores `TMPDIR`; with the wrapper nothing appears in `java.io.tmpdir` per call.

## Measured (AOT training covers library indexing)

Before this change the training workload only looked up JDK types, so the code that indexes a newly added library
jar (ASM scan, sources-jar parse, javadoc HTML via jsoup) and Vineflower ran cold on the first call after a dependency
was added or bumped. `dist/install.sh` now first runs `io.github.vecluu.jdoc.cli.TrainingLibrary` in its own,
non-recording JVM (javac + javadoc in-process, ~1 s): it writes a tiny library in three flavours (sources jar,
javadoc jar only, binary only; packages `jdoc.training.{sources,javadoc,binary}`) into the training repository plus
the `dependency:list` output. The recording run then executes the JDK workload, followed by 22 calls with
`--from ../library` in a second module: the first one resolves it and indexes the three jars lazily, the others run
`class`, `method`, `find`, `member`, `package`, `impls`, `source` on each flavour (`source` on the binary flavour
decompiles), and `index`. If the library cannot be generated the installer warns and trains the JDK workload only.

`scripts/bench-startup.sh` gained three "first call after adding a dependency" rows (`BENCH_NEW_DEP=0` skips them):
a different small library (`bench.fresh`, compiled once by the script with the JDK's `javac`/`javadoc`/`jar`, so not
the training library) gets a new version before every run, declared in a second module's POM and listed by the fake
`mvn`. Each measured call therefore re-resolves the module and indexes a jar it has never seen.

Same jar for both columns (working tree with the in-progress signature and `find` ranking changes, built
2026-11-02), installed once with the installer from before this change and once with the new one; 3 interleaved
rounds of `bench-startup.sh` (11 runs after 2 warm-ups each), median of the round medians, wrapper flags +
`-XX:AOTMode=on`, load ~3 (other agents building).

| Command | no cache | AOT, JDK training (before) | AOT, JDK + library training (after) |
| --- | --- | --- | --- |
| `--version` | 38 ms | 50 ms | 55 ms |
| `class java.util.Optional` | 169 | 92 | 97 |
| `method java.util.Optional#orElseThrow` | 153 | 84 | 88 |
| `find HashMap` | 185 | 109 | 116 |
| `member orElseThrow` | 148 | 84 | 89 |
| `source java.util.Optional#orElseThrow` | 161 | 91 | 95 |
| new dependency with sources jar, `class` | 422 | 167 | 154 |
| new dependency with javadoc jar only, `class` | 314 | 187 | 155 |
| new dependency, binary only, `source` (decompiled) | 789 | 599 | 477 |

| Install | before | after |
| --- | --- | --- |
| `install.sh` wall time (incl. training) | 6.4 s | 8.4 s (+1 s generating the library, +1 s library workload) |
| `lib/jdoc.aot` | 37.5 MB | 43.7 MB (+16.7 %) |

Findings:

- The first call after adding a dependency gets 13 ms (sources), 32 ms (javadoc) and 122 ms (decompile) faster.
  `-Xlog:class+load` on such a call: 5,378 classes from the cache, 52 not, of which 9 are ASM classes. ASM is
  compiled for class file version 49 and the JVM refuses to archive old-version classes that were linked during
  training (`Skipping org/objectweb/asm/...: Old class has been linked`); nothing to do short of another ASM build.
- What remains of the new-dependency cost is execution, not class loading: with the cache, indexing the 3-type
  library takes ~50 ms (debug log `indexed ... in 49 ms`) and re-resolving the module ~20 ms (fake `mvn` fork
  included); Vineflower's decompile stays ~400 ms of interpreted/C1 work. The JDK 25 cache stores no compiled code.
- Warm JDK lookups got ~4–6 ms slower in every round (and `--version` +5 ms): the cache maps 6 MB more. This is the
  price of the gain above; trimming the decompile part of the workload would win back some of the size but give up
  the largest gain (−122 ms). Kept as is; the cache grew well under the 30 % budget.
- Measured on `/tmp` (tmpfs): on a disk-backed `~/.cache/jdoc` the indexing commit adds an fsync.

## Experiments (reproducible)

Scratch dir: `/tmp/claude-1000/-home-rave-IdeaProjects-jdoc/fc95a69f-0953-495a-80f4-29f342833d65/scratchpad/startup/`
(`bench.py` timer, `harness/` with `Repeat`, `Phases`, `Split`, `Pico`, `Hello`, `daemon/` prototype,
`asmtool/Bump.java` picocli re-targeting).

```sh
# fake module (in proj/): pom.xml as in the task; fake mvn writes the dependency list to -DoutputFile=
printf '\nThe following files have been resolved:\n   none\n' > "$outputFile"
export JDOC_CACHE=$S/cache JDOC_MVN=$S/fakemvn

# sqlite native library, once
unzip -j -o jdoc.jar 'org/sqlite/native/Linux/x86_64/libsqlitejdbc.so' -d native
L="-Dorg.sqlite.lib.path=$S/native -Dorg.sqlite.lib.name=libsqlitejdbc.so"
F="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:-UsePerfData"

# AOT training (one step) and use
java $F $L -XX:AOTCacheOutput=jdoc.aot -jar jdoc.jar method java.util.Optional#orElseThrow
java $F $L -XX:AOTCache=jdoc.aot -Xlog:aot=off -Xlog:cds=off -jar jdoc.jar method java.util.Optional#orElseThrow

# AOT training (two step, no JAVA_TOOL_OPTIONS leak to child processes)
java $F $L -XX:AOTMode=record -XX:AOTConfiguration=jdoc.aotconf -jar jdoc.jar method java.util.Optional#orElseThrow
java $F $L -XX:AOTMode=create -XX:AOTConfiguration=jdoc.aotconf -XX:AOTCache=jdoc.aot -jar jdoc.jar

# check the cache is really used (fails hard if not)
java -XX:AOTMode=on -XX:AOTCache=jdoc.aot ... -jar jdoc.jar ...

# self-maintaining dynamic CDS alternative
java $F $L -XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=jdoc.jsa -jar jdoc.jar ...

# diagnostics used
java -Xlog:class+load:file=cl.log ...                     # class counts by source
java -XX:AOTCacheOutput=x.aot -Xlog:aot*=debug,cds*=debug:file=aot.log ...   # why classes are skipped
java -XX:StartFlightRecording=filename=x.jfr,settings=prof.jfc ...   # 1 ms ExecutionSample profile
```

Picocli re-target (scratch tool, ASM from the jar): for every `picocli/**/*.class`, `ClassReader` →
`ClassVisitor` that rewrites `visit(version=V1_8, ...)` → `new ClassWriter(COMPUTE_FRAMES | COMPUTE_MAXS)`, then
re-jar with the original manifest. The rewritten jar passed the `method`, `class`, `find`, `source` and `member`
calls above with identical exit codes.

## Sources

- [JEP 483: Ahead-of-Time Class Loading & Linking](https://openjdk.org/jeps/483)
- [JEP 516: Ahead-of-Time Object Caching with Any GC](https://openjdk.org/jeps/516)
- [Spring docs: JVM AOT Cache (training constraints)](https://docs.spring.io/spring-framework/reference/integration/aot-cache.html)
- [Inside.java: Run Into the New Year with Java's Ahead-of-Time Cache Optimizations](https://inside.java/2026/01/09/run-aot-cache/)
- [Gunnar Morling: JEP 483](https://www.morling.dev/blog/jep-483-aot-class-loading-linking/)
- [xerial/sqlite-jdbc README (GraalVM native-image support)](https://github.com/xerial/sqlite-jdbc/blob/master/README.adoc)
- [Azul: running CRaC](https://docs.azul.com/crac/usage/running-crac) and
  [Azul Zulu vs. Zing CRaC](https://docs.azul.com/crac/azul-zulu-zing-crac-compared)
