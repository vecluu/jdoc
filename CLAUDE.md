# jdoc

An offline Java 25 CLI that shows coding agents the signatures and Javadoc of the JDK and of the Maven
dependencies a module uses. User-facing docs: `README.md`.

## Where to look

| Question | Read |
| --- | --- |
| What jdoc does, why, what it does not do (known limitations) | `docs/SPEC.md` |
| How the code is built: binding rules, test rules, the cross-package paths | `docs/ARCHITECTURE.md`; per package, its `package-info.java` |
| Rules for an implementing agent | `docs/AGENT_GUIDE.md`; brief shape `docs/TASK_TEMPLATE.md` |
| Backlog: bugs, features, tasks | GitHub issues, labelled `area:<area>` (areas below) |
| Cutting a release | `docs/RELEASING.md` |
| Why a past decision was made (startup, skill trigger, licences) | `docs/decisions/` |

Only read what your task needs. Where a doc disagrees with the code, the code wins; fix the doc.

Areas (issue labels) are package groups: `foundation` = `model`, `error`, `config`, `query`, `text`;
`resolution-index` = `module`, `index`; `indexing` = `javadoc`, `source`, `bytecode`, `decompile`, `indexer`;
`lookup-cli` = `lookup`, `app`, `cli`; `build-delivery` = build, launcher, installer, scripts, AOT training, CI,
release.

## Build and test

Use the Maven wrapper (`./mvnw`); it needs JDK 25.

```sh
./mvnw -B verify                                   # unit (*Test, surefire) + integration (*IT, failsafe) tests, target/jdoc.jar
./mvnw -B test -Dtest='FooTest'                    # quick unit loop
./mvnw -B verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='FooIT'   # one IT
```

- When several agents share the working tree, each builds into its own directory: add `-Djdoc.buildDir=target/<id>`.
- Scripts and end-to-end checks (`scripts/smoke-test.sh`, `scripts/install-test.sh`) are described in `README.md`,
  "Building from source".

## Workflow

GitHub issue → local brief → implementing agent → notes → orchestrator verifies (`./mvnw -B verify`), commits and
keeps the docs below current. The maintainer approves each wave of briefs (disjoint files per wave), every push and
every GitHub write; only the orchestrator writes to GitHub. `tasks/` holds briefs named after their issue
(`tasks/<N>-<slug>.md`; the issue number is the task's only ID), agent notes and the wave board; it is local and
ignored by git, so the public trace of a task is its issue and its commits.

## Commits, pushes, releases

- Commit messages: Conventional Commits with a gitmoji, `<gitmoji> <type>(<scope>): <subject>`, for example
  `🐛 fix(index): ...`. One scoped, compiling change per commit; split unrelated changes.
- Issue numbers go in commit messages, never in code or docs (links to external bug reports
  are fine): `Refs #N` on work commits, `Closes #N` on the task's last commit.
- Only the orchestrating session commits. Implementing agents do not commit, push or branch (unless their brief
  allows a throwaway CI branch); they propose a commit split in their notes.
- Ask the maintainer before every push to `main`, every tag and every release. Exception: to test CI, push a
  throwaway branch and open a draft PR; close it and delete the branch afterwards.

## Keep these current

- Docs next to the code (`package-info.java`, Javadoc, comments): whoever changes the code (`JavadocPresenceTest`).
- Shared docs, by the orchestrator in the same commit as the change: `docs/SPEC.md` (behaviour, commands, output,
  limitations), `docs/ARCHITECTURE.md` (rules, request path, dependencies), `README.md` (commands, install,
  requirements), `THIRD-PARTY-NOTICES`/`NOTICE` (bundled dependencies). Agents report needed changes under
  "Doc impact" in their notes (`docs/AGENT_GUIDE.md`).
- Not `CHANGELOG.md`: it is compiled when a release is cut (`docs/RELEASING.md`).

## Public repository

Everything committed is public. No home directories, absolute local paths, machine names or hardware details
outside benchmark write-ups, no notes on which tools happen to be installed locally, no personal data.
