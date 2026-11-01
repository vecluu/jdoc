# T11 — Hide unusable inherited protected members

**Packages owned:** `lookup` (class view / inherited members), plus `app` tests if output changes.
**Depends on:** T09, T10 (read their notes).
**Spec:** `OUTLINE.md` "Commands and output" (class view example: `inherited from Object: getClass, notify,
notifyAll, wait`), "Goal and decisions" (visibility: public and protected).

## Problem
`jdoc class java.util.Optional` prints `inherited from Object: getClass, clone, notify, notifyAll, wait, finalize`.
`clone` and `finalize` are protected; a final class cannot be subclassed, so no caller can ever use them through it.

## Goal
- For a **final** class (and for enums and records, which cannot be subclassed by users), omit inherited
  **protected** members from the inherited-names lines and from inherited-member lookup in `jdoc method`
  (a direct request like `jdoc method Optional#clone` should then report not found, exit 1, ideally with a hint
  that the member is protected in `Object`).
- Non-final classes and interfaces keep showing inherited protected members (subclasses may call them).
- Own declared protected members of a final class are unaffected (rare, but they are part of the declared API).
- Decide whether `finalize` (deprecated for removal) should still be shown for non-final classes; keep it, the
  deprecated flag is already in the index — document the choice.

## Tests
- Unit tests in `lookup` for final class, enum, record, non-final class, interface.
- Extend `EndToEndIT` (or the class-view app test) so the Optional view matches the outline example line exactly.
