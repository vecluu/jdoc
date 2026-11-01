# T12 — Camel-hump matching for members in `jdoc find`

**Packages owned:** `index` (one new repository query), `lookup` (`FindService`).
**Depends on:** T03, T07, T09 (read their notes).
**Spec:** `OUTLINE.md` "Commands and output" (`jdoc find`: case-insensitive substring or camel humps).

## Problem
Members are pre-filtered by a SQL substring search before T07's `NamePattern` runs, so a hump pattern that is not
also a substring never matches a member: `jdoc find gFB` misses `getFooBar`. Types work because their names are
listed in full.

## Goal
- Add a repository method that returns the **distinct member names** in the artifact scope (or a pre-filter that
  is a superset of every possible hump match, e.g. names starting with the pattern's first character,
  case-insensitively, when the pattern contains an uppercase letter). Choose the approach by measuring: the JDK
  alone has ~65k members; the query plus matching must stay well under 100 ms on a warm index.
- `FindService` uses it for hump patterns; plain lowercase patterns keep the substring path.
- Ranking and output stay as they are (exact > prefix > humps > substring, shorter first).

## Tests
- Repository unit test for the new query (scoping, distinctness).
- `FindService` unit tests: `gFB` → `getFooBar`, `oET` → `orElseThrow`, lowercase pattern unchanged.
- A timing assertion (generous bound) in an IT against the real JDK index, skipped if `src.zip` is absent.
