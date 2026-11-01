# T06 — Decompiler (Vineflower) and decompiled cache

**Packages owned:** `decompile`.
**Depends on:** T01.
**Spec:** `OUTLINE.md` "Doc sources and fallback" (order 3, "Decompiled bodies are shown only through
`jdoc source`"), "Index and cache" (`decompiled/<g>/<a>/<v>/`, written once).

## Goal

Return Java source text for a top-level class in a binary jar, decompiled once with Vineflower and cached on disk.
T09 parses the returned text with T04's parser to slice method bodies for `jdoc source`.

## Scope

1. **`Decompiler` interface** — decompile one top-level type (including its nested/inner classes, which live in
   separate `Outer$Inner.class` entries) from a jar → source text.
2. **`VineflowerDecompiler`** — uses Vineflower's embedding API in-process (no `System.exit`, no console output on
   stdout: route Vineflower's logger to slf4j at debug or discard). Feed only the needed class entries (the outer
   class and its `$` nested entries) rather than the whole jar, for speed. Options: keep generic signatures, keep
   parameter names where present, no banner/comments noise, deterministic output.
3. **`DecompiledSourceCache`** — wraps a `Decompiler`: path `decompiled/<groupPath>/<artifactId>/<version>/<entry>.java`
   from `CachePaths`; write atomically (temp file + move) so concurrent processes never read half-written files;
   if present, read instead of decompiling. For JDK classes this path is never used (JDK always has sources).
4. Errors (corrupt class, decompiler failure) become a `JdocException` subclass with the type name in the message.

## Tests
- Unit tests: cache path computation, cache hit avoids calling the decompiler (fake `Decompiler`), atomic write
  behaviour, error wrapping.
- Integration test `VineflowerDecompilerIT`: decompile classes from the T01 sample library binary jar (a generic
  class with overloads, an enum, a record, a class with a nested class) and assert method names and bodies appear
  and that the output parses with the JDK compiler's parser (use `javax.tools`/`JavacTask.parse()` directly in the
  test if T04's parser is not available yet). Second call hits the cache (check file mtime unchanged).
