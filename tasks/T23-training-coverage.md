# T23 — AOT training covers library indexing

**Owns:** `dist/install.sh`, `cli.TrainingWorkload` (and a fixture generator next to it), `InstallerIT`; may touch
the license IT if needed (see below).
**Depends on:** T18 (read notes T17, T18, T21 first; `docs/STARTUP-RESEARCH.md` T17 section).
**Spec:** `tasks/T17-aot-wrapper-installer.md`, `OUTLINE.md` "Build and airgap transfer".

## Why
The AOT cache holds jdoc's own classes. Training today only exercises JDK lookups, so the code that indexes a new
library jar (bytecode scanning, sources-jar parsing, javadoc HTML via jsoup) and `source` on a decompiled type
(Vineflower) run cold on the first call after a dependency is added or bumped — exactly when an agent meets an
unfamiliar library.

## Scope
1. **Training library:** at install time, produce a tiny throwaway library in a temp fake Maven repo, in three
   flavours: with a sources jar, with only a javadoc jar, and binary only. Generate it with the JDK's own compiler and
   javadoc tools (as the test fixtures do) — do **not** check binary jars into the repo. Generation must run in a
   separate, non-recording JVM step before the AOT recording run, so compiler/javadoc tool classes are not recorded
   unnecessarily.
2. **Training workload:** extend it to index those jars (fake `mvn` listing them) and run `class`, `method`, `find`,
   `member`, `package` on library types of each flavour and `source` on the binary-only type (Vineflower). Keep the
   existing JDK workload.
3. **Measure** with `scripts/bench-startup.sh` plus a new "first call after adding a dependency" scenario (index a
   fresh library jar): before/after timings, training time and cache size before/after. Record them in
   `docs/STARTUP-RESEARCH.md`. If the cache grows by more than ~30%, report it and justify or trim.
4. ~~License files~~ — already done by T18 (`<prefix>/share/doc/jdoc/`, asserted by the install test); nothing to do.
5. If the training fixture is generated from code inside `jdoc.jar`, make sure T21's `LicenseNoticesIT` still
   passes (training classes are jdoc's own code, not a third-party dependency).

## Tests
- `InstallerIT`: training succeeds with the library flavours; the cache is used for a library lookup; a lookup of a
  freshly added library jar works with the cache and leaves stderr empty.
- Unit tests for the fixture generator and the extended workload where practical.
