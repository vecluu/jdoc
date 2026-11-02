# T26 — Make AOT training-library generation reliable

**Owns:** `cli.TrainingLibrary` (and its helpers), the training steps in `dist/install.sh`, `InstallerIT`.
**Depends on:** T25 (read notes T17, T23, T25).
**Spec:** `tasks/T23-training-coverage.md`.

## Problem
`InstallerIT.installedLauncherUsesTheAotCacheAndFallsBackSilentlyWhenTheJarChanges` failed once during a full
verify: the install output lacked "trained on the JDK and the training library", i.e. generating the training
library failed and the installer silently fell back to JDK-only training. It passed on re-run and in several other
runs, so the failure is intermittent. In CI that is a random red build; on a user's machine it is a silently weaker
AOT cache.

## Goal
Find the root cause, fix it, and make any remaining fallback visible.

## Scope
1. **Reproduce:** run the generation step and the IT repeatedly (e.g. 20–30 iterations, also under parallel load
   such as a concurrent `./mvnw verify` in another build dir) and capture the failing generator's stderr. Candidates:
   timeouts under load, temp-directory or file-name collisions, javac/javadoc tool exit codes, locale/encoding,
   output that the installer parses, races with the parallel JVM steps.
2. **Fix the root cause.** Do not just raise timeouts unless the evidence shows load-related timeouts and the new
   bound is justified.
3. **Visibility:** when the installer falls back to JDK-only training, print one clear warning line including the
   generator's reason (first line of its error), and exit 0 as before. `InstallerIT` asserts the success path and
   has a separate test for the fallback path (simulate a generator failure deterministically).
4. Report the iteration counts and results before and after the fix in your notes.

## Tests
- The repeated-run evidence (not a committed test) plus deterministic tests for both paths.
- Full `verify` green; `scripts/install-test.sh` still passes.
