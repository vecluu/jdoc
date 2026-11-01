# T05 — Bytecode reading (ASM) and javadoc-jar HTML

**Packages owned:** `bytecode`, and the HTML-page classes in `javadoc` (T04 owns the doc-comment classes in the
same package; the shared HTML-fragment-to-text rules and block-tag/first-sentence helpers were written by T01 —
use them, do not duplicate them).
**Depends on:** T01.
**Spec:** `OUTLINE.md` "Doc sources and fallback" (order 2 and 3, other rules), "Index and cache";
`docs/ARCHITECTURE.md` "Rendered signature format".

## Goal

Without any source code, know what types and members a binary jar contains, with signatures in the same rendered
format as T04, and extract per-type and per-member docs from a javadoc jar.

## Scope

1. **`BinaryJarScanner`:** enumerate class entries of a jar (skip `module-info`, `META-INF/versions/**` — base
   entries win, synthetic/anonymous/local classes). Produces, per class, a `TypeDescriptor` and its
   `MemberDescriptor`s via the class reader. Streams or iterates; does not load the whole jar into memory.
   Also lists which `package-info.class` entries exist.
2. **`ClassFileReader`** (ASM `ClassVisitor`, `SKIP_CODE`): kind (class/interface/enum/record/annotation),
   visibility (top-level from access flags; nested from the `InnerClasses` attribute), modifiers text, type
   parameters and supertypes from the generic `Signature` attribute (fallback to descriptors), deprecated flag
   (`@Deprecated` or `ACC_DEPRECATED`), members (public/protected only, skip synthetic and bridge methods, skip
   `<clinit>`, constructors named by simple type name, enum constants distinguished from fields, implicit enum
   `values`/`valueOf` kept), parameter names from `MethodParameters` else `argN`, varargs flag, throws clause from
   signature/exceptions. Rendered signatures use simple names (`java.util.List<java.lang.String>` →
   `List<String>`, nested `Map$Entry` → `Map.Entry`) and must be **identical** to what T04 renders from source for
   the same member wherever information allows (write a test that compares both on the sample library).
   No source offsets (absent ranges).
3. **Javadoc jar HTML (`JavadocHtmlReader` or similar, jsoup):**
   - Locate a type's page: `a/b/Outer.Inner.html` for nested types; package page `a/b/package-summary.html`.
   - Extract the class description (main block of the JDK 11+ layout, `section.class-description` /
     `div.block`), and per-member detail sections keyed by their anchor id. JDK 11+ anchors look like
     `of(java.lang.Object)` / `of(T)`; JDK 8 anchors look like `of-java.lang.Object-`. Provide anchor generation
     from a `MemberDescriptor` (erased, fully qualified parameter types where available) and fuzzy matching by
     name + arity when exact anchors miss (older layouts: doc only where anchors match, as the outline says).
   - Render HTML to `RenderedDoc` with the shared plain-text rules: `<code>` → backticks, links → their text,
     `<p>` → blank line, `<pre>` kept, `dl.notes` sections (`Parameters:`, `Returns:`, `Throws:`, `Since:`) →
     the same `@param`/`@return`/`@throws`/`@since` lines as T04, deprecation block → `@deprecated` line.
   - First sentence for each type and member (feeds the index).

## Tests
- Unit tests: access-flag → kind/visibility mapping, generic signature rendering (bounded type vars, wildcards,
  nested generics, arrays, varargs, inner classes of generic classes), skipping synthetic/bridge, anchor
  generation for both layouts, HTML-to-text conversion of representative snippets (inline small HTML strings).
- Integration tests:
  - `SampleLibraryBytecodeIT`: scan the T01 sample library binary jar; assert the full member list and that
    signatures match T04's source-rendered signatures (if T04 is not merged yet, assert against expected strings
    and leave a note).
  - `JavadocJarIT`: build the sample javadoc jar via the T01 fixture (real JDK 25 javadoc output) and assert type
    and member docs render as expected, including nested types and overloads.
  - `JdkJmodSmokeIT` (optional): read a few classes from the running JDK via the `jrt:/` filesystem to exercise
    real-world class files.

## Note from T01
Javadoc HTML renders `{@link Foo#bar(int)}` as link text `Foo.bar(int)`, but the outline requires `Foo#bar(int)`
(which T04 produces from source). When a link's `href` has a member fragment (`#bar(int)`), render it in the
`Type#member(params)` form so both doc sources agree; add a test that compares T04 and T05 output for the same
sample-library comment once both exist.
