# T14 — Replace picocli with a small hand-written command-line parser

**Packages owned:** `cli`, `Main`, and **`pom.xml`** (sanctioned: remove the picocli dependency only).
**Depends on:** T10 (read its notes and the `cli` source).
**Spec:** `docs/STARTUP-RESEARCH.md` (picocli costs ~90–140 ms cold and cannot be stored in the JDK 25 AOT cache
because it ships Java 5 bytecode); `OUTLINE.md` "Commands and output" (commands, names and flags, exit codes).

## Goal
Remove picocli entirely while keeping the CLI's observable behaviour identical: same commands, options, help text
shape, `--version`, usage errors (one `error:` line on stderr, exit 3), and pagination footer command line.

## Scope
- A small, object-oriented parser: a command registry (name → command definition with its positional arguments,
  options and one-line description), a tokenizer for `--opt value`, `--opt=value`, flags (`--force`), `--`
  end-of-options, unknown option / missing argument / extra argument errors with clear messages.
- Shared options `--from <path>` and `--page <n>` (positive integer) for every lookup command; `index --force`.
- `jdoc --help`, `jdoc <command> --help`, `jdoc` with no arguments (prints usage, exit 3? — keep T10's behaviour),
  `jdoc --version` (version from the jar manifest, as now).
- Help text is written by hand and kept short; it is what an agent sees from `jdoc --help`, so make it precise:
  one line per command with its argument syntax, the flags, and the exit codes.
- No reflection, no annotations scanning, no static initialisation of heavy components: the parser must load
  only a handful of small classes. Verify with `-Xlog:class+load` that no picocli class remains and report the
  number of jdoc classes loaded for `--version`.
- Remove the picocli dependency from `pom.xml`; nothing else in the pom may change. Update the dependency row in
  `docs/ARCHITECTURE.md`.

## Tests
- Unit tests for the tokenizer/parser (every option form, every error), help and version output.
- Existing CLI and `EndToEndIT` tests must pass unchanged in their expectations (adapt only test plumbing that
  referenced picocli types).
- Report before/after wall time of `java -XX:TieredStopAtLevel=1 -jar target/jdoc.jar --version` (median of 15).
