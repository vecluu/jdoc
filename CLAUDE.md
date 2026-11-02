# jdoc

An offline Java 25 CLI that shows coding agents the signatures and Javadoc of the JDK and of the Maven
dependencies a module uses. User-facing docs: `README.md`.

## Where to look

| Question | Read |
| --- | --- |
| What jdoc does, why, what it does not do (known limitations) | `docs/SPEC.md` |
| How the code is built: packages, binding rules, test rules, flows | `docs/ARCHITECTURE.md`, then `docs/codemap/<area>.md` for your area |
| Rules for an implementing agent | `tasks/AGENT_GUIDE.md` |
| Workflow, tasks in flight, backlog | `tasks/README.md`; brief shape `tasks/TEMPLATE.md` |
| Cutting a release | `docs/RELEASING.md` |
| Why a past decision was made (startup, skill trigger, licences) | `docs/decisions/` |

Only read what your task needs. Where a doc disagrees with the code, the code wins; fix the doc.

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

## Commits, pushes, releases

- Commit messages: Conventional Commits with a gitmoji, `<gitmoji> <type>(<scope>): <subject>`, for example
  `🐛 fix(index): ...`. One scoped, compiling change per commit; split unrelated changes.
- Only the orchestrating session commits. Implementing agents do not commit, push or branch (unless their brief
  allows a throwaway CI branch); they propose a commit split in their notes.
- Ask the maintainer before every push to `main`, every tag and every release. Exception: to test CI, push a
  throwaway branch and open a draft PR; close it and delete the branch afterwards.
- Work is organised as task briefs in waves, each wave approved by the maintainer (`tasks/README.md`).

## Keep these current

- `docs/SPEC.md` when behaviour, commands, output, options or limitations change.
- `docs/ARCHITECTURE.md` and `docs/codemap/*.md` when packages or key classes are added, renamed or moved
  (`CodeMapIT` checks names and paths).
- `THIRD-PARTY-NOTICES` and `NOTICE` when a bundled dependency changes (`LicenseNoticesIT` checks them).
- `README.md` when commands, options, install steps or requirements change.
- Not `CHANGELOG.md`: it is compiled when a release is cut (`docs/RELEASING.md`).

## Public repository

Everything committed is public. No home directories, absolute local paths, machine names or hardware details
outside benchmark write-ups, no notes on which tools happen to be installed locally, no personal data.
