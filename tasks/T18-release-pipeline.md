# T18 — Release packaging, smoke test, skill files and CI pipeline

**Owns:** `scripts/assemble.sh`, `scripts/smoke-test.sh`, `it/sample-project/`, `dist/skill/SKILL.md`,
`dist/AGENTS-snippet.md`, `.github/workflows/build.yml`; small fixes to `dist/` files from T17 if packaging needs it.
**Depends on:** T17 (wrapper + installer), T19 (real-Maven fixes), all notes. Needs a real `mvn` on PATH locally.
**Spec:** `OUTLINE.md` "Skill and AGENTS.md", "Build and airgap transfer", "CI pipeline (GitHub Actions)" — the
drafts there are the starting point; adapt them to what was actually built (T14 CLI, T17 installer).

## Scope
1. **`it/sample-project`:** small single-module Maven project depending on a few real, popular libraries with
   sources jars (e.g. commons-lang3, plus one with only a javadoc jar or none, to exercise fallbacks).
2. **`scripts/smoke-test.sh <jar> <project>`:** as drafted (fetch dependencies and sources online once, then run
   offline with a temp `JDOC_CACHE`), extended to: run every command once, check exit codes 0/1/2, check headers
   (`[from javadoc jar]`/`[decompiled, no docs]` where expected), and run `jdoc --help`.
3. **`scripts/assemble.sh <version>`:** reproducible tarball as drafted, with whatever T17 added to the bundle
   (e.g. nothing pre-trained: the AOT cache and sqlite extraction happen at install time and must never be in the
   bundle). `SHA256SUMS` beside it.
4. **Install check:** the smoke test (or a separate `scripts/install-test.sh`) extracts the tarball and runs
   `install.sh` into a temp prefix/bindir/skills dir, then calls the installed wrapper — this is the path users take.
5. **Skill files:** `dist/skill/SKILL.md` and `dist/AGENTS-snippet.md` from the outline; verify every command, flag
   and exit code mentioned matches the CLI's real `--help`.
6. **`.github/workflows/build.yml`:** from the outline draft, using `./mvnw` instead of `mvn` for the build, Java 25
   Temurin, the install check, artifact upload, and the tag-only `release` job with attestation. Pin actions to
   commit SHAs. Keep write permissions only on the release job.
7. **Version:** `-Drevision` flows into the jar manifest (`jdoc --version`) and the bundle `VERSION` file.

## Tests
- Run `assemble.sh`, the install check and `smoke-test.sh` locally and report results and timings in the notes.
- `actionlint` and `shellcheck` if available (do not require them).
- Do not push. The orchestrator asks the user before any push that is not a pipeline test.

Environment note: Maven 3.10.0 is at `/opt/maven/apache-maven-3.10.0/bin/mvn`. Agent shells may not have it on PATH; prepend that directory to PATH in your commands (or set `JDOC_MVN`). Do not hardcode the path in code or committed scripts.
