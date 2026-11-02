# T22 — Lookup quality: parameter names, find ranking, interface noise

**Packages owned:** `bytecode`, `javadoc` (HTML side), `source` (signature rendering only), `indexer` (merging
names), `lookup` (`FindService` ranking), `index` (schema version bump only), plus tests in `app`/`cli` whose
expected output changes.
**Depends on:** T18 (read notes T04, T05, T08, T09, T12, T15, T16 first).
**Spec:** `docs/ARCHITECTURE.md` "Rendered signature format"; `OUTLINE.md` "Commands and output".

## Why
Most agent calls are about libraries the model knows least. Three gaps make those answers worse than they need to be.

## Scope
1. **Real parameter names for javadoc-only types.** Types whose doc source is JAVADOC get signatures from bytecode,
   which usually lacks parameter names (`arg0`, `arg1`). The javadoc HTML member signature contains the real names
   (JDK 11+ layout and the JDK 8 layout). Extract them in the HTML reader and, at index time, replace the bytecode
   names when the member matches exactly (name + erased parameter types); never guess on a mismatch. Re-render the
   stored signature and `ParameterInfo`. Verify with a JDK-8-built javadoc jar (T19 used commons-lang3 3.5 from
   `~/.m2`; skip that IT if absent) and with the sample library's javadoc jar.
2. **`find` prefers the module's libraries.** At equal match score, rank types and members from the module's
   classpath artifacts above JDK types (`jdoc find Document` in a jsoup project lists `org.jsoup.nodes.Document`
   before `javax.swing.text.Document`). Keep score ordering otherwise unchanged. Apply the same tie-break to
   `jdoc member` output order for consistency.
3. **No implicit-modifier noise for interfaces.** Do not render `abstract` on interface methods or `public static
   final` on interface fields/constants (keep `default` and `static` methods, keep `abstract` in abstract classes).
   Source (T04) and bytecode (T05) rendering must stay byte-identical — extend `SampleLibraryBytecodeIT`'s comparison
   to cover interface members. Update the format in `docs/ARCHITECTURE.md`.
4. **Re-index:** stored signatures change, so bump the schema version once (existing indexes are rebuilt).

## Tests
- Unit tests for HTML parameter-name extraction (both layouts, generics, varargs, arrays, nested types), the merge
  rule (match, mismatch, partial), ranking tie-break, and interface rendering on both sides.
- Update affected expected outputs (EndToEndIT, app tests) and say which ones changed in the notes.
- Report before/after output for one javadoc-only member and for `find Document` in a module with jsoup.
