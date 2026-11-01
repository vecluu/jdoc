# T16 — Hot-path audit and startup benchmark harness

**Packages owned:** any package, but only for hot-path changes justified by measurements (lazy initialisation,
avoiding needless work); `src/test` for the benchmark IT; a new `scripts/bench-startup.sh`.
**Depends on:** T14, T15 (read their notes).
**Spec:** `docs/STARTUP-RESEARCH.md` (time breakdown, "the query itself 155 ms" without cache, 75 ms with).

## Goal
After picocli is gone and docs come from SQLite, find and remove what is still slow on a warm call, and leave a
repeatable benchmark behind so regressions are visible.

## Scope
- `scripts/bench-startup.sh <jar>`: creates a temp project with a fake `mvn` (as in the research doc), a temp
  `JDOC_CACHE`, warms the index, then reports the median of N runs for `--version`, `class java.util.Optional`,
  `method java.util.Optional#orElseThrow`, `find HashMap`, `member orElseThrow`, `source java.util.Optional#orElseThrow`.
  Accepts extra JVM flags via an environment variable so T17 can benchmark with the AOT cache.
- Profile a warm `method` call (`-Xlog:class+load` counts per package, `-Xlog:startuptime`/`-Xlog:init`, simple
  `System.nanoTime` probes behind a debug flag if useful). Typical suspects: slf4j-simple init, POM parsing via
  JAXP (could the POM key check skip parsing when file mtimes are unchanged since the cached meta?), repeated
  `stat` calls, SQLite connection setup (pragmas, WAL checkpoint), statement preparation, eager service creation,
  regex compilation, `String.format`-heavy rendering.
- Implement only changes with a measured benefit; keep the OO structure and all tests green.
- Record before/after numbers per command in `tasks/notes/T16.md` and add a "Measured" section to
  `docs/STARTUP-RESEARCH.md`.

## Tests
- Unit tests for any changed logic (e.g. a fast-path POM key check must still detect every change the old one did:
  edits, local parent edits, SNAPSHOT parent mtime).
- The benchmark script is not part of `verify`; add one lightweight IT that runs it with N=1 to keep it working.
