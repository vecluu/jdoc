# T02 — Module and version resolution

**Packages owned:** `module`.
**Depends on:** T01.
**Spec:** `OUTLINE.md` "Version resolution", "Index and cache" (modules/ files), "Refresh rules" (first bullet),
open question "Sibling modules"; `docs/ARCHITECTURE.md`.

## Goal

Given a start path (`--from` file or the working directory), produce a `ModuleClasspath` for the enclosing Maven
module, invoking Maven only when the module's POM key changed.

## Scope

1. **Module locator:** walk up from a file or directory to the nearest `pom.xml`; that directory is the module.
   Not found → a clear `JdocException` (exit 3 later).
2. **POM reader:** read just what jdoc needs with the JDK DOM parser (namespaces optional, no network, no
   external entities): own groupId/artifactId/version (inheriting groupId/version from `<parent>`), `<parent>`
   coordinates and `<relativePath>` (default `../pom.xml`; empty element means "no local parent"), `<modules>`,
   and declared dependencies (groupId/artifactId only, needed for the unresolved fallback).
3. **POM key:** SHA-256 over the module POM plus every local ancestor POM reached through `<relativePath>` (only
   if that file's coordinates match the declared parent). Parent POMs that come from the repository are not part of
   the key, except SNAPSHOT parents, which add their repository file's mtime. Output: lowercase hex.
4. **Maven invoker:** interface plus a process-based implementation running the exact command from the outline in
   the module directory (`-o -q -B dependency:list -DincludeScope=test -DoutputAbsoluteArtifactFilename=true
   -DoutputFile=<deps.txt>`), with the executable from `JdocEnvironment` and a timeout. Capture stderr for the
   warning line; never let Maven output reach stdout. If the run in the module directory fails and a reactor root
   exists above it, retry once from the reactor root with `-pl <relative module path> -am`
   (answers the "sibling modules" open question defensively).
5. **`deps.txt` parser:** robust against all real formats of maven-dependency-plugin 3.x: leading whitespace,
   header lines ("The following files have been resolved:"), `none`, optional classifier, the
   `-- module <name> [(auto)]` suffix, Windows-free paths with spaces. Produces `ResolvedDependency` list;
   skips non-jar types (e.g. `pom`) and entries whose jar file does not exist.
6. **Module meta:** `meta.json` with module path, POM hashes and resolved-at. Hand-write a minimal flat JSON
   codec (string/number fields only); no JSON library.
7. **Reactor detection:** collect the GAV coordinates of the project's own modules (walk up local parents to the
   reactor root, then down through `<modules>`), and drop them from the classpath.
8. **Failure handling (`ClasspathResolver`, the public entry point):**
   - Cache hit (`modules/<key>/deps.txt` exists) → parse, status RESOLVED, no Maven run.
   - Miss → run Maven; on success write deps.txt and meta.json.
   - Maven fails → use the most recent good list for the same module path (find it via `meta.json` files),
     status STALE, one warning line (`warning: mvn failed, using classpath resolved at <time>`).
   - No list at all → status UNRESOLVED: for each declared dependency pick the newest version directory found in
     the local repo that contains the jar; versions compared with Maven-like ordering (numeric segments, qualifiers,
     SNAPSHOT lower than release).
   - Nothing usable and Maven failed → `JdocException` (becomes exit 3).

## Tests
- Unit tests: locator, POM reader (parent inheritance, relativePath variants), key calculation (changes when a local
  parent changes, stable otherwise, SNAPSHOT mtime rule), deps.txt parser with several real-looking samples,
  version comparator, meta codec, reactor detection, resolver decision logic with a fake `MavenInvoker`.
- Integration test `ClasspathResolverIT`: a temp multi-module project (parent + two modules) and a fake Maven
  executable (a small shell script set via `JdocEnvironment`) that writes a deps.txt; verify cache hit avoids a
  second run, POM edits trigger re-resolution, failure falls back to the last good list, and reactor siblings are
  excluded. Do **not** require a real Maven installation.
