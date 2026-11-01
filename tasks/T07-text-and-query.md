# T07 — Text output primitives and query primitives

**Packages owned:** `text`, `query`.
**Depends on:** T01.
**Spec:** `OUTLINE.md` "Commands and output" (all of it, especially names and flags, output cap, examples);
`docs/ARCHITECTURE.md`.

## Goal

Pure, side-effect-free building blocks for formatting results and for interpreting the user's query arguments.
Everything here is plain logic and should have exhaustive unit tests.

## Scope

### `text`
1. **Token budget and pagination:** estimate tokens as characters / 4; page budget ~2,000 tokens (make the budget a
   constructor parameter, default 2,000). Split a result (list of lines, with a header line and optional "blocks"
   that should not be split across pages where possible, e.g. one method's Javadoc) into pages. Every page repeats
   the header line. A capped page ends with exactly `page 1/3 · next: <command line> --page 2` (the original command
   line is passed in, with any existing `--page` removed). Last page of a multi-page result ends with `page 3/3`.
   Out-of-range page → a clear message and "not found" semantics for the caller.
2. **Column alignment:** two-column layout as in the `jdoc class` and ambiguity examples (left column padded to
   the widest entry, capped at a maximum width so one long signature does not push everything right; overly long
   left entries just get two spaces).
3. **Result header line:** builds the single header line every result starts with: type display name with type
   params, modifiers and kind keyword, artifact label in parentheses, then tags (`[from javadoc jar]`,
   `[decompiled, no docs]`, `[version unresolved]`). Match the examples in the outline exactly, e.g.
   `java.util.Optional<T>  public final class  (jdk 25)`.
4. **`CommandResult`**-ready text model: a small immutable value holding header + lines/blocks (T10 adds the exit
   code side; coordinate by keeping this value in `text`, named e.g. `TextResult`).

### `query`
1. **Type reference parsing:** `<type>` may be an FQCN, a simple name, `Outer.Inner`, `Outer$Inner`, or
   `pkg.Outer.Inner`. Produce the list of candidate interpretations (binary-name forms) that T09 will try against
   the index, without touching the index.
2. **Member reference parsing:** `<type>#<name>[(<params>)]`: optional param list, empty parens meaning "zero
   params" vs. no parens meaning "all overloads"; params are comma separated simple names, whitespace tolerant;
   generics in input are erased (`List<String>` → `List`); varargs `String...` ≡ `String[]`; fully qualified input
   params reduced to simple names. Constructors: `Type#Type(...)` and `Type#<init>(...)` both accepted. Invalid
   syntax → descriptive error value.
3. **Overload matcher:** given parsed params and a member's `ParameterInfo` list, decide a match using erased simple
   type names (case-sensitive, arity must match). Nested type params match either `Entry` or `Map.Entry`.
4. **Name matcher for `jdoc find`:** case-insensitive substring OR camel humps (`HSR` matches
   `HttpServletRequest`, `HSRequest` too, `hsr` lowercase also allowed as humps); returns a match score used for
   ordering (exact simple name > prefix > humps > substring; shorter names first).

## Tests
- Unit tests only (everything is pure), but thorough: pagination boundaries (exact fit, one char over, block larger
  than a page, page out of range, header repeated), footer formats, alignment cap, header format for each kind and
  tag combination, every query syntax form including malformed input, overload matching incl. varargs/arrays/
  generics/nested, camel-hump edge cases (single letter, digits, all caps names like `URLDecoder`, `XMLHttpRequest`).
- One integration-style test `PaginationScenarioIT` that paginates a realistic ~25k-character class view and checks
  every page is within budget and the pages concatenate back to the full content.
