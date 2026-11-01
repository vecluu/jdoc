# T04 — Java source parsing and doc-comment rendering

**Packages owned:** `source`, and the doc-comment classes in `javadoc` (T05 owns the HTML-page classes in the same
package; the shared HTML-fragment-to-text rules and block-tag/first-sentence helpers were written by T01 — use
them, do not duplicate them).
**Depends on:** T01.
**Spec:** `OUTLINE.md` "Doc sources and fallback" (javadoc rendering rules), "Commands and output" (signature
style in the class example); `docs/ARCHITECTURE.md` "Rendered signature format", `TypeDescriptor`,
`MemberDescriptor`, `RenderedDoc`.

## Goal

Turn the text of one `.java` file into model descriptors with exact source offsets, and turn Javadoc comments into
compact plain text. Used for sources jars, the JDK `src.zip` and decompiled output.

## Scope

1. **`JavaSourceParser`:** parse a single compilation unit from a `String` with the javac Tree API
   (`JavacTask.parse()` only — no `analyze()`, no classpath, no annotation processing). Must handle all Java 25
   syntax found in the JDK's own `src.zip` (records, sealed/permits, enums with bodies, annotations, module-info and
   package-info files). Reuse one compiler/file manager instance per parser for speed; the JDK has ~15k files.
2. **Extraction:** produce a parsed-file result with `TypeDescriptor`s for every top-level and nested member type
   (skip local and anonymous classes) and `MemberDescriptor`s for each. Rules:
   - Visibility, including implicit rules: interface/annotation members are public; nested types in interfaces are
     public and static; enum constants public; record components produce accessors only if declared or implicit
     (include implicit record accessors and canonical constructor if not declared); enum `values()`/`valueOf`
     implicit methods included.
   - Supertypes as `TypeName`s: resolve simple names from source **without attribution** using a best-effort
     resolver: same-file nested types, explicit single-type imports, same package, `java.lang`, then on-demand
     imports (record the first on-demand package as a guess; this is acceptable and documented).
   - Rendered signatures exactly per the architecture format; `ParameterInfo` erasure rules.
   - Deprecated flag from `@Deprecated` or `@deprecated` tag.
   - `SourceRange` for each member (from doc comment start through end of body) and for the doc comment alone;
     declaration range for types.
   - `package-info.java`: package doc comment exposed separately.
3. **`DocCommentRenderer`:** `DocCommentTree` (via `DocTrees`) → `RenderedDoc`, following the outline rules:
   `{@code x}` and `<code>` → backticks; `{@link Foo#bar(int)}` / `{@linkplain}` → `Foo#bar(int)` (label if
   given); `<p>` → blank line; `<pre>` blocks preserved verbatim (including `{@code}` inside them); other HTML
   stripped; entities decoded; `@param`, `@return`, `@throws`/`@exception`, `@since`, `@deprecated` one line each;
   `{@inheritDoc}` and a missing comment set `inheritsDoc` (resolution across types is T09's job). Also `@see`
   (one line), `{@literal}`, `{@value}`, `{@summary}`, `@apiNote`/`@implNote`/`@implSpec` (keep as one-line
   labelled sections). First sentence as javadoc defines it (`{@summary}` if present).
   Inline tags are your job; HTML inside comment text goes through T01's shared formatter so T05's output is
   identical. If a shared rule is wrong, fix it there (it is a sanctioned shared class) and note it.
4. **Lookup by offset:** given source text and a member's `SourceRange`, return the doc and/or body slice (used by
   `jdoc source` in T09).

## Tests
- Unit tests: signature rendering for every member kind and modifier combination, generics, varargs, arrays,
  nested types, implicit members (records, enums, interfaces), supertype name resolution, erasure in
  `ParameterInfo`, every Javadoc rendering rule individually, first-sentence edge cases (`e.g.` abbreviations,
  `{@summary}`), offsets slicing.
- Integration tests:
  - `SampleLibraryParseIT` against the T01 sample library sources.
  - `JdkSourceParseIT`: parse a curated set of real `src.zip` files (`java/util/Optional.java`,
    `java/util/Map.java` (nested `Entry`), `java/lang/String.java`, `java/lang/Record.java`, an enum, an annotation,
    `java/util/concurrent/ConcurrentHashMap.java`) and assert key signatures/docs. Plus one test that parses
    **all** of `java.base` from `src.zip` without errors and reports the time (skip if `src.zip` is absent).
