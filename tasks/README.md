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
| 8 | T17 AOT cache, wrapper, installer | alone | T16 (cache trained on final classes) |

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
| T11 | todo | `tasks/notes/T11.md` |
| T12 | todo | `tasks/notes/T12.md` |
| T13 | todo | `tasks/notes/T13.md` |
| T14 | todo | `tasks/notes/T14.md` |
| T15 | todo | `tasks/notes/T15.md` |
| T16 | todo | `tasks/notes/T16.md` |
| T17 | todo | `tasks/notes/T17.md` |
