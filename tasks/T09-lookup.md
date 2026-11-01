# T09 — Lookup services (read side)

**Packages owned:** `lookup`.
**Depends on:** T01, T03, T04, T05, T06, T07 (read their notes). T08 runs in parallel; do not depend on its
classes — populate the index in tests through T03's repositories and T04/T05 descriptors directly.
**Spec:** `OUTLINE.md` "Commands and output" (semantics of every command), "Doc sources and fallback"
(`{@inheritDoc}`, decompiled bodies only via `source`), "Goal and decisions" (visibility, inherited members,
ambiguous names); `docs/ARCHITECTURE.md`.

## Goal

Answer every question a command asks, scoped to a set of artifact ids, returning **structured result values**
(not formatted text — formatting is T10's job). Expected outcomes are values: found, not found, ambiguous with
candidates.

## Scope

1. **Scope value:** the set of artifact ids plus their `ArtifactKey`s/labels and resolution status (accept it as a
   constructor/method argument; T10 builds it from T08's output — define a small interface or value here that
   T10 can adapt to).
2. **`TypeResolver`:** `<type>` argument → found / not found / ambiguous (list of candidates with artifact labels).
   Use T07's type-reference parsing; try FQCN/binary forms first, then nested display names, then simple names.
   Only API-visible types count (public/protected; nested types need an API-visible enclosing chain).
   The same FQCN in two artifacts on the classpath is not ambiguous: pick the first in classpath order and note it.
3. **`TypeHierarchy`:** direct and transitive supertypes in javadoc order (superclass chain first, then
   interfaces breadth-first, `java.lang.Object` last), and transitive subtypes for `impls` (with artifact labels;
   subtypes from any artifact in scope). Unknown supertypes (not indexed) are kept by name and skipped silently.
4. **`ClassViewService`:** data for `jdoc class`: descriptor, first doc paragraph (load on demand, see 6), own
   API members (signature + first sentence, declared order; constructors, then fields/enum constants, then
   methods is fine — document your choice), inherited member **names** grouped by declaring type (exclude members
   overridden/hidden by a closer type, exclude constructors, `Object` included as in the outline example).
5. **`MemberLookupService`:** for `Type#name[(params)]`: candidates on the type itself; if none, on supertypes
   (inherited methods are valid targets, report the declaring type); overload filtering with T07's matcher;
   result lists every matching overload. Not found → suggestions are optional (e.g. members with similar names).
6. **`DocLoader`:** full `RenderedDoc` on demand for a type, member or package:
   - SOURCES / JDK: read the type's entry from the sources jar or `src.zip`, parse with T04, find the member by
     its stored offsets (fallback: by name + erased params), render the doc comment.
   - JAVADOC: read the type's HTML page from the javadoc jar via T05 and pick the member section.
   - DECOMPILED: no doc.
   - `{@inheritDoc}` and undocumented overrides: walk `TypeHierarchy` to the nearest supertype member with the same
     name and erased params that has a doc; substitute.
   - Cache parsed files within one call (an agent call touches few files).
7. **`SourceLoader`** (`jdoc source`): member body plus its Javadoc. SOURCES/JDK: slice by offsets from the
   source text. JAVADOC/DECOMPILED: decompile the top-level type via T06's cache, parse with T04, slice the member;
   mark the result as decompiled.
8. **`FindService`:** `jdoc find <pattern>`: types and members whose names match (T07 name matcher); ordered by
   score, capped to a sensible maximum before pagination; each with FQCN (`Type` or `Type#member(params)`).
9. **`MemberSearchService`:** `jdoc member <name>`: every API type declaring a member with that name, as
   `Type#name(params)`.
10. **`PackageService`:** `jdoc package <pkg>`: `package-info` doc (from sources, javadoc `package-summary.html`,
    or none) and the package's API types with first sentences.

## Tests
- Unit tests for each service using a real temp SQLite index populated through T03 repositories with small,
  hand-built descriptors, and fakes for jar/entry readers where useful. Cover ambiguity, nested names
  (`Outer.Inner`, `Outer$Inner`), visibility filtering, overrides hiding inherited names, overload selection,
  inheritDoc resolution through a superclass and through an interface, decompiled path with a fake `Decompiler`.
- Integration test `LookupIT`: index the T01 sample library (binary + sources + javadoc jars, with some sources
  removed) by calling T04/T05 directly into T03 repositories (a small test helper is fine — note it; T10 will later
  test through the real indexer), then exercise every service end to end against real jars, including `source`
  through real Vineflower.

## Decisions from the orchestrator (binding)
- Overload matching stays lenient (declared `T` or erased `Object` both match); simple names like `Entry` keep
  matching nested types (`Map.Entry`) — with several candidates this correctly yields "ambiguous".
- **Sanctioned change outside your package (T07 `query`):** `NamePattern` must treat a pattern as camel humps only
  when it contains at least one uppercase letter (IntelliJ style). An all-lowercase pattern like `map` is a plain
  case-insensitive substring match. Update T07's tests and note the change in your notes.
- T08 runs in parallel and owns `indexer` plus a small change to `source` (record methods); do not edit those.
