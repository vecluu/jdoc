# T20 — Project README

**Owns:** `README.md` (repository root). Read-only everywhere else.
**Depends on:** T18, T22, T23, T24 (final CLI output, installer, bundle, pipeline and startup numbers exist; read all notes).
**Spec:** `OUTLINE.md` (whole), `docs/ARCHITECTURE.md`, `docs/STARTUP-RESEARCH.md` "Measured", the real `jdoc --help`.

## Goal
A concise public README for https://github.com/vecluu/jdoc that lets a developer understand, install and use jdoc,
and lets a contributor build it — without duplicating the outline.

## Content
- One-paragraph pitch: what jdoc is (offline Java library docs CLI for coding agents), why (exact signatures at the
  module's resolved versions, few hundred tokens per answer, no guessing/decompiling).
- Quick example: 2–3 real command outputs captured from the built jar (`class`, `method`, an ambiguous name with
  exit 2). Capture them, do not invent them.
- Requirements (Linux, Java 25, Maven for version resolution), install from a release (download, verify attestation
  and checksum, airgap transfer, `install.sh` options), and the opencode skill / `AGENTS.md` snippet.
- Command reference table (all commands, `--from`, `--page`, exit codes) matching `--help` exactly.
- How it works in five bullets (resolution, index, per-type doc source fallback, cache location, refresh), linking
  to `OUTLINE.md` and `docs/ARCHITECTURE.md` for depth.
- Performance: measured warm-call latency with and without the AOT cache (from the research doc's measured section).
- Building from source (`./mvnw verify`, the fat jar, smoke test, assemble) and a short "Contributing" section
  describing the task-brief workflow (`tasks/`) and the commit convention (conventional commits + gitmoji).
- CI badge for the `build` workflow; license section only if a LICENSE file exists (if none, say so in your notes —
  do not add one; the orchestrator asks the user).

## Done when
Every command, flag, path and number in the README is verified against the built artifacts; notes list what was
verified and how.
