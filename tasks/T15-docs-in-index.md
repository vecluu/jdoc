# T15 — Store rendered docs in the index; no javac/jsoup on the lookup hot path

**Packages owned:** `index` (schema + repositories), `indexer` (render and store docs), `lookup` (`DocLoader`,
`PackageService`, and any service that currently parses on demand), plus test updates in `app` if needed.
**Depends on:** T11, T12, T13 (they change `lookup`, `index` and the schema first; read their notes), T03–T10 notes.
**Spec:** `docs/STARTUP-RESEARCH.md` section 2 and the time breakdown (javac init ~130 ms, jsoup ~35 ms per call).

## Decision change (binding)
`OUTLINE.md` says the index stores only the first doc sentence and parses full text on demand. **This is
reversed:** the index now stores the full `RenderedDoc` for every API type, member and package at index time.
Update `docs/ARCHITECTURE.md` ("Database" and "Runtime flow") to record the new decision and the reason
(startup cost). `OUTLINE.md` has already been updated by the orchestrator.

## Goal
`class`, `method`, `find`, `member`, `impls` and `package` answer purely from SQLite: no `jdk.compiler`, no jsoup,
no ASM, no Vineflower classes loaded (verify with `-Xlog:class+load`). Only `source` and indexing may load them.

## Scope
1. **Schema:** store the rendered doc (main text, block-tag lines, `inheritsDoc` flag) for types, members and
   packages (new package-doc table). Choose a compact encoding; consider SQLite page size and whether to compress
   large texts — measure. Bump the schema version (an old index is rebuilt automatically).
2. **Indexer:** when parsing sources (T04) or javadoc HTML (T05), keep the full `RenderedDoc` it already produces
   and write it. DECOMPILED types store no doc. Measure and report: JDK index time before/after (must stay under
   ~10 s) and `index.db` size before/after for the JDK and for the sample library.
3. **Lookup:** `DocLoader` reads docs from the index. `{@inheritDoc}` / undocumented overrides are still resolved at
   lookup time by walking `TypeHierarchy` over **stored** docs (supertypes may live in other artifacts, so do not
   pre-resolve across artifacts at index time). Same-artifact pre-resolution is optional.
4. **`source` command:** unchanged behaviour; it still reads the jar entry / decompiles, and is the only lookup path
   that may load javac or Vineflower.
5. **Lazy wiring:** ensure the composition root does not construct parser/jsoup/decompiler components unless the
   command needs them (indexing or `source`).

## Tests
- Repository round-trip tests for stored docs (including empty and very long docs, `<pre>` blocks, unicode).
- Indexer tests asserting docs are stored for SOURCES, JAVADOC and JDK types and absent for DECOMPILED.
- Lookup tests: inheritDoc across artifacts from stored docs.
- An IT that runs `method`, `class`, `find`, `package` in-process and asserts (via a class-loading probe, e.g. checking
  `Class.forName` was never triggered through a guarded factory, or a child JVM with `-Xlog:class+load`) that no
  `com.sun.source`, `org.jsoup`, `org.objectweb.asm` or `org.jetbrains.java.decompiler` class was loaded.
- `EndToEndIT` output expectations unchanged.
