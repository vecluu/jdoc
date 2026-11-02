# Releasing jdoc

How to cut a release of any version. Every step that pushes, tags or publishes needs the maintainer's explicit
approval. Run from the repository root. `X.Y.Z` is the new version (Semantic Versioning; a version with `-`, such as
`1.0.0-rc.1`, becomes a prerelease).

```sh
REPO=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
V=X.Y.Z
PREV=$(git describe --tags --abbrev=0)   # the last release tag
```

## Release quirks

- **`CHANGELOG.md` is written at release time, not during development.** Agents never edit it while working on a
  task. `scripts/release-notes.sh <version>` fails when `CHANGELOG.md` has no non-empty `[<version>]` section, and CI
  runs it first on tags and dry runs, so step 1 below is required.
- **Dispatch dry runs on a branch, never on a tag ref.** Until T32 lands, the release job publishes whenever the ref
  is a `v*` tag, even with `release-dry-run` set.
- **Reproducible builds depend on the JDK's zlib.** The same commit, version and `SOURCE_DATE_EPOCH` give the same
  jar entries and timestamps everywhere, but the compressed bytes (and so the checksums) match only a build with the
  CI toolchain (Temurin 25 on `ubuntu-24.04`). Check a release against a CI dry run, not a local build.

## Steps

1. **Compile the changelog** (normal scoped commit on `main`). Read `git log --no-merges "$PREV"..HEAD` and write a
   `## [X.Y.Z] - <release date>` section in `CHANGELOG.md` ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
   headings: Added, Changed, Fixed, Removed). Take the user-visible commits (`feat`, `fix`, `perf`, and anything that
   changes commands, output, install or requirements) and rewrite their subjects for users; leave out tests,
   refactoring, CI and agent docs. Copy the current "Known limitations" from `docs/SPEC.md` into a
   `### Known limitations` subsection. Keep an empty `## [Unreleased]` heading above it and update the compare links
   at the bottom. Update the "latest is" sentence in `README.md`. Do not change `<revision>` in `pom.xml`: CI takes
   the version from the tag.
2. **Push and wait for green** (approval): `git push origin main`, then
   `gh run list -R "$REPO" --workflow build.yml --branch main --limit 1`. Check that `git status --short` is empty
   and that `git rev-parse origin/main` equals `SHA=$(git rev-parse HEAD)`.
3. **Dry run** (recommended; publishes nothing):
   `gh workflow run build.yml -R "$REPO" --ref main -f release-dry-run=$V`, then
   `gh run watch <id> -R "$REPO" --exit-status`, and keep its `SHA256SUMS`
   (`gh run download <id> -R "$REPO" -D "$(mktemp -d)"`). The tag build must give the same checksum.
4. If the release date differs from the one in `CHANGELOG.md`, fix it first (new commit, back to step 2).
5. **Tag** (approval): `git tag -a "v$V" -m "jdoc $V" "$SHA"` and `git push origin "v$V"`.
6. **Watch the tag run**:
   `RUN=$(gh run list -R "$REPO" --workflow build.yml --event push --branch "v$V" --limit 1 --json databaseId --jq '.[0].databaseId')`
   and `gh run watch "$RUN" -R "$REPO" --exit-status`. The build and release jobs must both succeed.
7. **Verify the release** in a temp directory (`D=$(mktemp -d) && cd "$D"`): `gh release view "v$V" -R "$REPO"`
   (title `jdoc X.Y.Z`, notes = changelog section plus install footer, two assets), then `gh release download "v$V" -R "$REPO"`,
   `sha256sum -c SHA256SUMS` (same as the dry run's) and `gh attestation verify "jdoc-$V.tar.gz" -R "$REPO"`.
8. **Install it** into temp directories: `tar xzf "jdoc-$V.tar.gz"`, then
   `./jdoc-$V/install.sh --prefix "$D/prefix" --bindir "$D/bin" --skills-dir "$D/skills"` and
   `JDOC_CACHE="$D/cache" "$D/bin/jdoc" --version` prints `jdoc X.Y.Z`. For the full check, from the repository:
   `scripts/install-test.sh "$D/jdoc-$V.tar.gz" it/sample-project`.
9. Check that the releases page shows the version as Latest (unless it is a prerelease).

**If something fails.** The build job failed: no release exists. Fix it on `main`, then (approval) delete the tag
with `git push origin ":refs/tags/v$V"` and `git tag -d "v$V"`, and start again at step 2. Only
`gh release create` failed (the attestation already exists): rerun the failed job with
`gh run rerun "$RUN" -R "$REPO" --failed`.

The local equivalent of the CI build (to debug a failing step) is in `README.md`, "Building from source".
