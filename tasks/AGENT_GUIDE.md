# Guide for implementing agents

You implement one task of `jdoc`, a Java 25 CLI described in `OUTLINE.md`. Read, in this order:

1. `docs/ARCHITECTURE.md` — binding contracts: packages, ownership, domain model, rules. Follow it.
2. The `OUTLINE.md` sections your task names (only the Java program matters; ignore CI, scripts, installer, skill).
3. `tasks/notes/*.md` of the tasks yours depends on — they describe the public API that already exists.
   Read the actual source of those classes when you need detail; do not re-implement what exists.

## Working rules

- **Stay in your packages.** Only create or edit files in the packages your task owns (main and test). If you need a
  change in another package (a missing accessor, a bug), make the smallest possible change, and list it under
  "Changes outside my packages" in your notes file. Never rewrite another task's design.
- **Do not edit `pom.xml`** and do not add dependencies. If one is unavoidable, stop and explain why in your notes.
- **Other agents work in the same tree at the same time.** Always build with your own build dir:
  `./mvnw -B -q -Djdoc.buildDir=target/<TASK-ID> test` (fast loop, add `-Dtest='YourTest*'`) and
  `./mvnw -B -q -Djdoc.buildDir=target/<TASK-ID> verify` before you finish. If compilation fails because of another
  task's half-written package, wait a minute and retry; do not touch their code to "fix" it.
- Keep your code compiling at all times; write files completely rather than in broken intermediate states.
- Do not commit, push or create branches. Do not delete files you did not create.
- No network access in code or tests. Tests must not touch the real `~/.cache/jdoc` or `~/.m2`.

## Code standards

- Object-oriented: single responsibility, small classes, interfaces at boundaries (filesystem, processes, database,
  jars), constructor injection, composition over inheritance, no static mutable state, no singletons.
- Domain values are immutable records; defensive copies of collections; `Optional` instead of `null` returns.
- Lombok for boilerplate (`@RequiredArgsConstructor`, `@Slf4j`, `@Builder`, `@Value`). No `@Data` on values,
  no `@SneakyThrows`.
- Only the `cli` package writes to stdout. Log with slf4j (stderr), mostly at `debug`/`warn`.
- Wrap checked exceptions at component boundaries into the `error` package's `JdocException` hierarchy.
- Javadoc on every public type and on non-obvious public methods. No commented-out code, no TODOs left behind
  unless listed in your notes.

## Testing standards

- JUnit Jupiter + AssertJ. No Mockito: write small fakes implementing the interfaces.
- **Unit tests** (`*Test`, surefire) for every non-trivial class: normal cases, edge cases, error cases.
- **Integration tests** (`*IT`, failsafe) for the larger pieces that combine real components, real files, real
  jars or a real SQLite file. Your task file says which ones are required.
- Use the shared fixtures from T01 (`src/test/java/.../testsupport`) to build real binary, sources and javadoc jars
  and fake Maven repositories in a `@TempDir`. Extend the fixtures if needed (note it in your notes file).
- Tests must be deterministic and fast (unit tests well under a second each). Tests that index the whole JDK
  `src.zip` are allowed only as ITs and only where your task says so.

## When you finish

1. `./mvnw -B -q -Djdoc.buildDir=target/<TASK-ID> verify` passes (report honestly if something does not).
2. Write `tasks/notes/<TASK-ID>.md` (keep it under ~80 lines):
   - public classes/interfaces you created, one line each: name — responsibility — key methods;
   - decisions or deviations from `docs/ARCHITECTURE.md` and why;
   - changes outside your packages; known gaps or follow-ups;
   - test summary (counts of unit/IT tests).
3. Reply to the orchestrator with a short summary (≤ 15 lines): status, test result, notable decisions, open issues.
   Do not paste code.

## Known pitfalls (from earlier tasks)

- Never start a test class name with `Test` (surefire's default pattern would also run an `*IT` named `Test...` as a
  unit test). Use `FooTest` / `FooIT`.
- `OUTLINE.md` is kept in sync with decisions by the orchestrator. If your task changes a spec-level decision,
  say so in your notes; do not edit `OUTLINE.md` yourself.
