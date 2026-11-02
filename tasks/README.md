# jdoc implementation tasks

Each `Txx-*.md` file is a self-contained brief for one implementing agent. The orchestrator injects
`tasks/AGENT_GUIDE.md` plus one task file into each agent.

## Waves (dependency order)

| Wave | Tasks | Runs in parallel? | Needs |
| --- | --- | --- | --- |
| 1 | T01 Foundation (config, model, errors, test fixtures) | alone | — |
| 2 | T02 Module resolution · T03 Index store · T04 Source parsing + doc comments · T05 Bytecode + javadoc HTML · T06 Decompiler · T07 Text output + query primitives | yes | T01 |
| 3 | T08 Indexer · T09 Lookup services | yes | wave 2 (T08: T03–T05; T09: T03–T07) |
| 4 | T10 Application services, CLI, end-to-end tests | alone | everything |
| 5 | T11 Inherited protected members · T12 Member camel humps · T13 Failed-jar memo · T14 Drop picocli | yes (T12/T13 touch `index` in separate classes, schema bump only in T13; T14 only `cli` + pom) | T10 |
| 6 | T15 Rendered docs stored in the index | alone (schema, indexer and lookup all change) | T11–T13 |
| 7 | T16 Hot-path audit + benchmark harness | alone | T14, T15 |
| 8 | T17 AOT cache, wrapper, installer · T19 Real-Maven validation · T21 License notices | yes (T17: `dist/`; T19: `module`; T21: pom, root license files, one IT) | T16; T19 needs Maven installed |
| 9 | T18 Release packaging, smoke test, skill files, CI | alone | T17, T19 |
| 10 | T22 Lookup quality · T23 AOT training covers libraries · T24 Skill design | yes (T22: lookup/rendering/indexer; T23: `dist/install.sh`, training workload; T24: `dist/skill`, `dist/AGENTS-snippet.md`, one doc) | T18 |
| 11 | T20 Project README | alone | T22, T23, T24 (captures final output, numbers and skill) |

## Status

| Task | Status | Notes file |
| --- | --- | --- |
| T01 | done | `tasks/notes/T01.md` |
| T02 | done | `tasks/notes/T02.md` |
| T03 | done | `tasks/notes/T03.md` |
| T04 | done | `tasks/notes/T04.md` |
| T05 | done | `tasks/notes/T05.md` |
| T06 | done | `tasks/notes/T06.md` |
| T07 | done | `tasks/notes/T07.md` |
| T08 | done | `tasks/notes/T08.md` |
| T09 | done | `tasks/notes/T09.md` |
| T10 | done | `tasks/notes/T10.md` |
| T11 | done | `tasks/notes/T11.md` |
| T12 | done | `tasks/notes/T12.md` |
| T13 | done | `tasks/notes/T13.md` |
| T14 | done | `tasks/notes/T14.md` |
| T15 | done | `tasks/notes/T15.md` |
| T16 | done | `tasks/notes/T16.md` |
| T17 | done | `tasks/notes/T17.md` |
| T18 | done | `tasks/notes/T18.md` |
| T19 | done | `tasks/notes/T19.md` |
| T20 | todo | `tasks/notes/T20.md` |
| T21 | done | `tasks/notes/T21.md` |
| T22 | done (uncommitted) | `tasks/notes/T22.md` |
| T23 | done (uncommitted) | `tasks/notes/T23.md` |
| T24 | done (uncommitted) | `tasks/notes/T24.md` |

## Backlog (follow-ups found during implementation; not yet scheduled)

Scheduled from the backlog: T22 (parameter names, find ranking, interface noise), T23 (library AOT training, license files in the install prefix).

- Fallback CDS archive only contains the classes of the call that created it (weak if that was `--version`);
  consider training it explicitly or regenerating it after a real lookup (T17 area).
- CI: `RealMavenResolutionIT` is skipped in CI because the dependency plugin is not in `~/.m2` yet; add a priming
  step before the build (T18 area).
- The jar is not byte-reproducible: set `project.build.outputTimestamp` in the pom (from `SOURCE_DATE_EPOCH`/git).
- The tag-only release job and its attestation step have never run; needs a decision on how to test it (a throwaway
  pre-release tag publishes a public release).
