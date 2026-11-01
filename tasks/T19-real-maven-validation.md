# T19 — Validate against real Maven and answer the outline's open questions

**Packages owned:** `module` (fixes), test sources; a test project under `src/test/resources` or built in `@TempDir`.
**Depends on:** T16 (it may change the POM key fast path in `module`); a real `mvn` on PATH (the user installs it).
**Spec:** `OUTLINE.md` "Version resolution", "Open questions" (sibling modules, resolution time, old javadoc jars).

## Goal
Every test so far used a fake `mvn`. Run jdoc against real Maven and real `~/.m2` content and fix what breaks.

## Scope
- Real `dependency:list` output with the installed Maven and maven-dependency-plugin version: confirm the parser,
  the `-DoutputFile` behaviour, and quiet/batch flags; capture a real sample as a parser test fixture.
- **Sibling modules:** a multi-module project (parent + `a` + `b` where `b` depends on `a`), siblings not installed:
  does `mvn -o dependency:list` work inside `b`? Verify the `-pl <module> -am` retry from the reactor root works
  and that `a` is excluded from the classpath. Record the answer in your notes.
- **Resolution time:** measure `dependency:list` on this repository itself (`jdoc` dogfooding: run the built jar
  inside `/home/rave/IdeaProjects/jdoc`) and on the multi-module project; record numbers.
- **Old javadoc jars:** if any javadoc-only artifact built with JDK 8 javadoc exists in `~/.m2`, check what jdoc
  extracts; otherwise note that it could not be tested.
- Dogfood: `jdoc class`, `method`, `find` for picocli-free deps of this repo (e.g. `org.jsoup.nodes.Document`,
  `org.objectweb.asm.ClassVisitor`) and report the output quality.
- Real-Maven ITs must **skip** (JUnit assumption) when `mvn` is not on PATH or the needed artifacts are not in
  `~/.m2`, so CI and other machines still pass offline. They must not download anything during `verify`.

## Done when
Open questions answered in the notes (orchestrator copies the answers into `OUTLINE.md`), bugs fixed with tests.
