# Guide for implementing agents

You implement one task brief of jdoc. `CLAUDE.md` (build, test and commit rules) is already loaded. Read, in order:

1. Your brief, `tasks/Txx-<slug>.md` (a local working file: `tasks/` is ignored by git; the brief's `Issue: #N` line
   names its GitHub issue).
2. `docs/ARCHITECTURE.md` (binding rules, test rules, the cross-package paths), then the `package-info.java` of each
   package you touch.
3. The `docs/SPEC.md` sections your brief names, when you change behaviour.

Read the source of existing classes when you need detail; do not re-implement what exists.

## Working rules

- **Stay in the files your brief owns** (main and test). If you need a change elsewhere (a missing accessor, a bug),
  make the smallest possible change and list it under "Changes outside my files" in your notes. `pom.xml`, `dist/`,
  `scripts/` and the workflow only when your brief owns them; never add a dependency without the orchestrator.
- **Other agents work in the same tree at the same time.** Build into your own directory:
  `./mvnw -B -q -Djdoc.buildDir=target/<TASK-ID> test` (add `-Dtest='YourTest*'`) and `... verify` before you finish.
  If compilation fails in another task's half-written package, wait and retry; never fix their code.
- **Skip the slow ITs while iterating** (`@Tag("slow")`: real JDK, real Maven, installs and JVMs):
  `./mvnw -B -q -Djdoc.buildDir=target/<TASK-ID> verify -DexcludedGroups=slow`. Run the full `verify` before you
  finish.
- Keep the code compiling; write files completely rather than in broken intermediate states.
- Do not commit, push, tag or branch (unless your brief allows a throwaway CI branch). Do not delete files you did
  not create. Do not edit `CHANGELOG.md`; it is written at release time.
- Never write to GitHub: no issues, labels, comments or PR edits. Put follow-ups you find under "Known gaps" in your
  notes; the orchestrator files them as issues after the maintainer approves.
- No task or issue numbers in code or committed docs ("T08's resolver", "#12"); describe the thing itself. Links to
  external bug reports are fine.
- If your change alters behaviour described in `docs/SPEC.md`, a rule or a package's purpose, update `docs/SPEC.md`,
  `docs/ARCHITECTURE.md` or the package's `package-info.java` in the same task (`DocReferencesIT` checks names and
  paths, `PackageDependencyTest` the package layering, `JavadocPresenceTest` that every package and public type is
  documented).

## When you finish

1. `./mvnw -B -q -Djdoc.buildDir=target/<TASK-ID> verify` passes with no new skipped tests (report honestly if not).
2. Write `tasks/notes/<TASK-ID>.md` (local, under ~80 lines): what you built (one line per public class), decisions and
   deviations, changes outside your files, known gaps, a test summary, and a proposed commit split
   (`<gitmoji> <type>(<scope>): <subject>` plus paths; the orchestrator adds `Refs #N` or `Closes #N`).
3. Reply to the orchestrator in at most 15 lines: status, test result, notable decisions, open issues. No code.
