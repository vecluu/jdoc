# T32 — Release-dispatch guard and JDK test prerequisites

**Owns:** `.github/workflows/build.yml`, `src/test/java/.../indexer/JdkIndexerIT.java`,
`src/test/java/.../source/JdkSourceParseIT.java`, the dry-run quirk in `docs/RELEASING.md` and the "Release
workflow" gotcha in `docs/codemap/build-and-delivery.md`.
**Depends on:** —
**Runs in parallel with:** T29 (it owns the rest of `docs/`; touch only the two lines named above there).
**Read first:** `docs/codemap/build-and-delivery.md` "Flows" and "Gotchas", `docs/RELEASING.md`,
`docs/ARCHITECTURE.md` "Tests".

## Why

1. A manual `workflow_dispatch` with `release-dry-run` on a `v*` tag ref publishes a real release: the release job's
   `PUBLISH` only checks that the ref is a tag, and the version step prefers the tag over the dry-run input.
2. `JdkIndexerIT` and `JdkSourceParseIT` skip with a plain `assumeTrue` when the JDK has no `lib/src.zip`, so a CI JDK
   without it would silently skip the only tests that index the real JDK.

## Goal

A dry run never publishes, whatever ref it runs on; and a missing `src.zip` fails those ITs in CI.

## Scope

1. **Workflow.** A run with a non-empty `release-dry-run` input never attests or publishes, on any ref. Decide whether
   a dispatch on a tag ref with the input should fail early or run as a dry run of the input version, and say which in
   the workflow comment. Tag pushes publish as before.
2. **ITs.** Replace the plain assumptions with `testsupport.Prerequisites` (skip locally, fail with `CI=true`).
3. **Docs.** Remove the "Dispatch dry runs on a branch, never on a tag ref" quirk from `docs/RELEASING.md` and update
   the gotcha in `docs/codemap/build-and-delivery.md` to the new behaviour.

Out of scope: other workflow changes, other backlog items.

## Tests

- Unit/IT: the two ITs still pass locally with a JDK that has `src.zip`.
- CI: prove the guard on a throwaway branch with a draft PR: a dispatch with `release-dry-run` shows "dry run, not
  publishing". Do not dispatch on a tag ref and do not create tags.

## Done when

- `./mvnw -B verify -Djdoc.buildDir=target/T32` passes with no new skipped tests.
- The CI evidence above, then the throwaway branch and draft PR are closed and deleted.
- Notes in `tasks/notes/T32.md` with a proposed commit split.

## Rules

Follow `tasks/AGENT_GUIDE.md`. You may push one throwaway branch and open a draft PR to test CI; ask before anything
else that touches the remote. Never tag.
