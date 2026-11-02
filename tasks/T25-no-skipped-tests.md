# T25 — No silently skipped tests

**Owns:** `module.RealMavenResolutionIT`, the shellcheck test in `cli.InstallerIT`, `.github/workflows/build.yml`
(test-related steps only), and the failsafe/surefire configuration in `pom.xml` (sanctioned: test configuration only).
**Depends on:** T23 (read notes T17, T18, T19, T21, T23).
**Spec:** `tasks/AGENT_GUIDE.md` testing standards.

## Problem
Three integration tests skip on every local run and in CI, so they protect nothing:
- `RealMavenResolutionIT` (2 tests) skips locally because `mvn` is not on the PATH of non-login shells, and in CI
  because the maven-dependency-plugin (or another plugin the offline probe needs) is not in `~/.m2` at that point.
- `InstallerIT` (1 test) skips because `shellcheck` is not installed locally, and apparently also in the CI Maven run
  even though a separate CI step runs shellcheck.

## Goal
These tests run and pass locally (on this machine) and in CI. A test may still skip on a machine that genuinely
lacks a prerequisite, but **never in CI**: there a missing prerequisite must fail the build.

## Scope
1. **Find a Maven reliably.** Do not depend on the shell's PATH. Prefer the Maven that is running the build: pass
   `maven.home` from the build into the failsafe JVM (system property) and use `<maven.home>/bin/mvn`; fall back to
   `JDOC_MVN`, then `PATH`. This also works under `./mvnw` and in CI.
2. **Make the offline prerequisites present.** Diagnose exactly which plugins/artifacts the IT's offline probe
   misses (run it in CI-like conditions: fresh `~/.m2` copy via `-Dmaven.repo.local` in a temp dir, then build). Make
   the normal build download them before the ITs run (e.g. declare the needed plugin versions so the build itself
   resolves them, or a `dependency:get`/`go-offline` style step bound before `integration-test`), so neither local
   runs nor CI need a manual priming step. Keep the IT itself offline.
3. **shellcheck.** In CI, install shellcheck before the Maven run so `InstallerIT`'s check runs inside `verify`
   (remove the duplicate standalone step only if the IT fully covers the same files). Locally the test keeps its
   assumption; the orchestrator will ask the user to install shellcheck on this machine.
4. **Fail instead of skip in CI.** When the environment variable `CI=true` (set by GitHub Actions) is present, the
   prerequisite checks in these tests must fail with a clear message instead of skipping. Implement it once (a small
   test helper) and use it in both tests.
5. Validate on GitHub with a throwaway branch + draft PR exactly as T18 did (worktree under the scratchpad, branch
   `ci/t25-skips`, never push to main, never tag, close the PR and delete the branch afterwards). The green run must
   show 0 skipped ITs in the failsafe summary — quote that line in your notes.

## Tests
- Local: `./mvnw -B verify` shows `RealMavenResolutionIT` running (not skipped) without any PATH changes.
- CI: failsafe summary with `Skipped: 0`.
