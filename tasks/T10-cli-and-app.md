# T10 — Application services, CLI and end-to-end tests

**Packages owned:** `app`, `cli`, `Main`.
**Depends on:** everything (T01–T09). Read all notes files first.
**Spec:** `OUTLINE.md` "Commands and output" (complete), "Goal and decisions", "Version resolution" step 4
(warning and `[version unresolved]`), the `JDOC_CACHE` note in the smoke-test section; `docs/ARCHITECTURE.md`
"Runtime flow" and "Exit codes".

## Goal

Wire everything into the eight commands with exact output formats and exit codes, and prove the whole program works
end to end.

## Scope

1. **`app` services** — one per command (`find`, `class`, `method`, `source`, `impls`, `member`, `package`,
   `index`). Each takes parsed arguments, uses T02 → T08 → T09 to answer, and formats with T07 into a
   `CommandResult` (exit code + `TextResult`). A shared "module context" step (resolve classpath, ensure index,
   build lookup scope) is one reusable collaborator, not copy-pasted per command. Output formats follow the outline
   examples exactly (header line first, ambiguity listing with `Re-run with a fully qualified name.`, class view
   layout, `inherited from X: a, b, c` lines). Decide and document formats the outline leaves open (find, impls,
   member, package, method with several overloads, index report) — keep them compact and consistent.
   - STALE resolution prints its one warning line on stderr; UNRESOLVED adds `[version unresolved]` to headers.
   - `index [--force]` prints counts and types with missing sources (grouped by artifact, capped with a count of
     the rest).
2. **`cli`** — picocli: top-level `jdoc` with `--help`/`--version` (version from the jar manifest), eight
   subcommands, shared mixin for `--from <path>` and `--page <n>`. Thin: parse, call the app service, paginate
   (T07) with the reconstructed command line for the footer, print, return the exit code. Usage errors → exit 3
   with picocli's message on stderr. Unexpected exceptions → one-line error on stderr, exit 3 (stack trace only
   at debug level). `Main` calls `System.exit` with the code; everything else is testable without exiting.
3. **Composition root** — one class constructing every concrete component from `JdocEnvironment` (the only place
   using `new` on services); closes the database at the end.
4. **Startup cost:** avoid work on the hot path: no indexing when nothing changed, lazy creation of heavy
   components (Vineflower, jsoup, javac) only when the command needs them. Measure a warm `jdoc method` call and
   report it in your notes.

## Tests
- Unit tests for each app service with fakes for the module-context collaborator (formatting, exit codes,
  pagination footer with the original command line).
- CLI tests running the picocli command in-process with captured stdout/stderr (argument parsing, help, usage
  errors, exit codes).
- End-to-end integration tests `EndToEndIT` (real everything, no network): a temp cache (`JDOC_CACHE`), a fake local
  Maven repo built with T01 fixtures, a temp Maven project, and a fake `mvn` script (via `JDOC_MVN`) that writes
  the deps.txt pointing at the fixture jars. Cover every command, every exit code (0/1/2/3), the outline's
  ambiguity example (`jdoc class Duration` → exit 2 listing `java.time.Duration` and
  `javax.xml.datatype.Duration` with `jdk 25`), the `java.util.Optional` class view shape, paging with `--page 2`,
  `--from` pointing into another module, refresh after a jar changes, STALE warning, UNRESOLVED tag, and
  `index --force`. Run the built fat jar once (`java -jar target/jdoc.jar --help`) in an IT to catch shading
  problems (services files, native SQLite load, `jdk.compiler` access).

## Done when
`./mvnw -B verify` passes from a clean checkout, and `java -jar target/jdoc.jar class java.util.Optional` run inside
any directory with a `pom.xml` and a working `mvn` produces the outline's class view.

## Follow-ups handed to T10
- T02: when `JDOC_M2_REPO` is set explicitly, pass `-Dmaven.repo.local=<repo>` to the Maven run so Maven and jdoc
  agree on the repository (small change in `module`; note it).
- T09: put the JDK **first** in the `LookupScope` artifact order (javac semantics: JDK wins when an FQCN appears twice).
- T09 open issue (decide, fix if cheap): `find` cannot match members by camel humps unless the pattern is also a
  substring (`gFB` misses `getFooBar`), because members are pre-filtered by substring in SQL. A fix needs a T03
  repository method listing member names in scope; acceptable to leave as a documented limitation.
- **Ordering conflict to resolve in your adapter:** T08's `IndexedClasspath` lists the JDK **last**; T09 wants it
  **first** in `LookupScope`. Build the scope with the JDK first (javac semantics) and test it with a library that
  declares a type with a JDK FQCN.
- **Required fix (T03 `index`, sanctioned):** `IndexDatabase.open` fails with SQLITE_BUSY when several processes or
  threads open a brand-new `index.db` concurrently (reproduced by T08 with 4 threads). Make schema creation safe
  under concurrency (e.g. take `IndexLock` or retry on SQLITE_BUSY around WAL setup and schema creation) and add a
  concurrent first-open test. Two agents running jdoc for the first time at once must both succeed.
- T08 gap (optional): a jar that keeps failing to index is retried under the lock on every call; consider
  remembering the failure keyed by jar path + mtime + size.
