# T17 — AOT cache, wrapper and installer (startup option 1, without the picocli rewrite)

**Owns:** `dist/bin/jdoc` (wrapper template), `dist/install.sh`, a training workload (script and/or hidden CLI
mode), `scripts/bench-startup.sh` usage; minimal Java changes only if the training workload needs them.
**Depends on:** T14, T15, T16 (the cache must be trained on the final class set; read their notes).
**Spec:** `OUTLINE.md` "Build and airgap transfer" (wrapper and installer as originally drafted, release bundle
layout), `docs/STARTUP-RESEARCH.md` section 1 (all validated commands, constraints and pitfalls). This task brings
the wrapper and installer into scope; CI pipeline, `assemble.sh` and the skill files are T18.

## Goal
An installed jdoc starts warm calls in roughly 200 ms or less, safely: a stale or mismatched cache never makes a
call slower or noisier than running without a cache.

## Scope
1. **Installer (`dist/install.sh`)**, extending the outline's draft (`--prefix`, `--bindir`, `--skills-dir`, Java 25
   check):
   - Pre-extract the sqlite-jdbc native library for the current architecture from the jar into the prefix once;
     the wrapper passes `-Dorg.sqlite.lib.path` / `-Dorg.sqlite.lib.name` so nothing is written to `/tmp` per call.
   - Train the JDK 25 AOT cache with the **two-step** record/create mode (not one-step: `JAVA_TOOL_OPTIONS` would
     leak into a real `mvn` child). The training workload runs representative commands (`index`, `class`,
     `method`, `find`, `member`, `package`, `source`) against a throwaway project with a fake `mvn` and a temp
     `JDOC_CACHE`, so it needs no network, no real Maven and does not pollute the user's cache.
   - Store a fingerprint next to the cache (jar path, jar mtime and size, `java -version` output / `release` file of
     the Java used) and the flags used. Re-running the installer rebuilds everything.
2. **Wrapper (`dist/bin/jdoc`, POSIX sh, filled in by the installer):**
   - Flags: `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:-UsePerfData`, the sqlite lib properties, and
     `-XX:AOTCache=<file>` only when a cheap fingerprint check passes (no extra JVM launch; compare stored values
     with a `stat` of the jar and the resolved `java` binary/`release` file).
   - On mismatch, fall back to `-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=<user cache dir>` (self-healing,
     ~330 ms) and never print JVM cache warnings: `-Xlog:aot=off -Xlog:cds=off` (verify the exact flags against the
     research doc).
   - Must stay `exec java ...` with `"$@"` passthrough and correct exit codes.
3. **Measure** with `scripts/bench-startup.sh` (from T16): no cache, AOT cache, fallback archive; record results in
   `docs/STARTUP-RESEARCH.md` "Measured" section and in your notes.

## Tests
- A Java IT (or shell test driven from an IT) that runs `install.sh` into a temp prefix/bindir with the built jar,
  then:
  - runs the installed wrapper and checks exit codes and output for `--version` and a JDK `method` lookup;
  - verifies the AOT cache is actually used (e.g. `-Xlog:aot` in a diagnostic run, or a wrapper debug env var);
  - touches the jar (mtime change) and verifies the wrapper falls back silently (no stderr noise, same output);
  - verifies no file is created under `/tmp` by sqlite per call.
  Skip gracefully (assumption) if the platform is not Linux.
- `shellcheck`-clean scripts if `shellcheck` is available (do not require it).

## Packaging note (added with T18 in scope)
The installer runs from the extracted release bundle (`OUTLINE.md` "Release bundle": `bin/jdoc`, `lib/jdoc.jar`,
`skill/SKILL.md`, `VERSION`, `install.sh`). Design paths for that layout; T18 assembles the tarball afterwards.
Anything produced at install time (AOT cache, sqlite library, fingerprint) lives under the install prefix, never
in the bundle.
