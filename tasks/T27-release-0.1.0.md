# T27 — Release 0.1.0

**Owns:** `pom.xml` (release-related build settings only), `CHANGELOG.md` (new), release notes, small fixes in
`scripts/assemble.sh` / `.github/workflows/build.yml` if the release path needs them.
**Depends on:** T26 (CI must be reliably green). Read notes T18, T20, T21, T25, T26 and `README.md`.
**Spec:** `OUTLINE.md` "Build and airgap transfer", "CI pipeline"; `README.md` install section.

## Goal
Ship `v0.1.0` as the first public GitHub release: tarball, `SHA256SUMS`, build provenance attestation, release notes.
**The agent prepares everything; pushing the tag is done only after the orchestrator has the user's explicit
approval** (a tag push publishes a public release and cannot be cleanly undone).

## Scope
1. **Reproducible jar:** set `project.build.outputTimestamp` (derived so that the same commit builds the same jar;
   coordinate with `SOURCE_DATE_EPOCH` used by `assemble.sh`). Prove it: two clean builds of the same commit give
   byte-identical `jdoc.jar` and tarball checksums.
2. **Version plumbing:** confirm `-Drevision=0.1.0` flows into `jdoc --version`, the bundle `VERSION`, the tarball
   name and `SHA256SUMS`; the default `revision` in the pom stays a SNAPSHOT for development builds.
3. **Release dry run (local):** build with `-Drevision=0.1.0`, assemble, verify the checksum, run
   `scripts/install-test.sh` and `scripts/smoke-test.sh` against the tarball, exactly as a user would per the README.
4. **Release job review:** read the tag-only `release` job line by line (permissions, artifact download,
   `actions/attest-build-provenance`, `gh release create` arguments, what gets uploaded). It has never run; fix
   anything that would break, and check the README's `gh attestation verify` command matches what the job produces.
   If a safe way exists to exercise the job without publishing a public release (e.g. running the same steps
   under `workflow_dispatch` with publishing disabled), propose it in your notes; do not push tags.
5. **Changelog and notes:** add `CHANGELOG.md` (Keep a Changelog format) with a concise 0.1.0 entry summarising
   the user-facing features (commands, offline resolution, doc-source fallback, AOT-accelerated startup, installer,
   skill) and known limitations (from the backlog and notes). Decide whether the release body uses
   `--generate-notes` (gitmoji commit list) or the changelog section; prefer the changelog section and adjust the
   job if needed.
6. **README:** make sure the install instructions name `0.1.0` correctly (or are version-neutral) and the badge,
   attestation and checksum steps match the release assets.
7. **Hand-off:** list the exact commands the orchestrator will run after approval (tag creation on the verified
   commit, tag push, watching the run, verifying the published assets with `gh release download` + `sha256sum -c` +
   `gh attestation verify`, and installing the downloaded tarball into a temp prefix).

## Done when
Local dry run passes, notes contain the hand-off commands and dry-run evidence, nothing is tagged or pushed.
