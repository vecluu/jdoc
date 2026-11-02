# T29 — Code map: flows, change recipes, test map, and a guard test

**Owns:** `docs/ARCHITECTURE.md` (new sections; the existing rules and package table only for fixes),
`docs/codemap/*.md`, a new `CodeMapIT` (plus a unit test for its parsing if non-trivial) in the root test package
`io.github.vecluu.jdoc`, next to `LicenseNoticesIT`.
**Depends on:** T31 (doc consolidation, done).
**Runs in parallel with:** T32. Do not touch `.github/`, `JdkIndexerIT`, `JdkSourceParseIT`, `docs/RELEASING.md`.
**Read first:** `docs/ARCHITECTURE.md`, all `docs/codemap/*.md`, `tasks/AGENT_GUIDE.md`.

## Why

`docs/ARCHITECTURE.md` is now the single code overview, but it has no per-command flows, no change recipes and no
test map, and nothing stops the maps from rotting: before T31, four files pointed at a `docs/CODEMAP.md` that never
existed.

## Goal

An agent can find, from `docs/ARCHITECTURE.md` alone, which classes a command passes through, which files and tests
a typical change touches, and which test guards which behaviour; and a renamed class or moved file named in the docs
fails the build with a message saying where.

## Scope

1. **Runtime flows** in `docs/ARCHITECTURE.md`: per command, the chain of key classes from `Main` to output; the
   index build; install-time AOT training and launcher start. Short chains, linking the area maps for detail.
2. **Change recipes:** which packages, files, tests and docs are touched, in which order, to add a command; add a CLI
   option; add a doc source or change the fallback order; change the index schema; change an output format; change
   the launcher or installer; add a dependency (licence notices); cut a release (pointer to `docs/RELEASING.md`).
3. **Test map:** which IT guards which behaviour, the slow ITs, the fixtures. Keep the test rules where they are.
4. **Area maps:** consistent headings, no duplication between them or with `docs/ARCHITECTURE.md`/`docs/SPEC.md`,
   no volatile numbers (line counts, current schema version, timings).
5. **`CodeMapIT`** (failsafe, fast, no network):
   - every package under `src/main/java/io/github/vecluu/jdoc` appears in the package table of `docs/ARCHITECTURE.md`;
   - every backticked class reference in `docs/ARCHITECTURE.md` and `docs/codemap/*.md` (`pkg.Type`,
     `pkg.Type#member`, a bare `UpperCamel` name — define the rule precisely in the test's Javadoc) resolves to a
     source file under `src/main` or `src/test`;
   - every backticked relative file path in those files and in `CLAUDE.md`, `docs/SPEC.md`, `docs/RELEASING.md` and
     `tasks/AGENT_GUIDE.md` exists in the repository;
   - failure messages name the stale entry and its file. A small, documented allow-list for JDK types and examples
     (`Optional`, `Type#name`) is fine.

Out of scope: behaviour changes, other docs, the T32 items.

## Done when

- `./mvnw -B verify -Djdoc.buildDir=target/T29` passes with no new skipped tests.
- Renaming a class named in a map, or a path named in `CLAUDE.md`, makes `CodeMapIT` fail with a clear message
  (try both, then revert).
- Notes in `tasks/notes/T29.md` with a proposed commit split.

## Rules

Follow `tasks/AGENT_GUIDE.md`. Nothing machine-specific (public repository).
