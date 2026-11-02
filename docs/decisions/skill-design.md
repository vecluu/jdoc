# Skill design: when is a jdoc call worth it?

Decision record for `dist/skill/SKILL.md` and `dist/AGENTS-snippet.md`. The first version of the skill said "call jdoc
before any library method you have not verified in this session". Read literally, that means a lookup before almost
every line that touches the JDK or a library. Most of those lookups would confirm what the model already knows
(`List.of`, `String.isBlank`, `Optional.orElseThrow`). This document sets the trigger deliberately.

## What a call costs

| Cost | Size | Source |
| --- | --- | --- |
| Wall time, warm | ~0.2-0.6 s | `docs/decisions/startup.md` "Measured"; 0.22 s with the build that rewrote the skill trigger |
| Wall time, first call in a module | ~6 s | the index is built once per module, then refreshed only on POM change |
| Tokens of the answer | ~50-200 (`find`, `member`, one overload), ~250-500 (`method` with one overload, `source`), ~800-2,000 (`class`, every overload of a heavily overloaded method) | measured on the sample project; the hard cap is ~2,000 tokens a page |
| Agent turns | one tool call, so one more model turn (re-reading the whole context) | |

The wall time hardly matters. What costs is the **turn** and the **tokens that stay in context** for the rest of the
session. A `class` page on a large type, or `method` without parameters on `List#of` (12 overloads, ~1,400 tokens),
costs more than the code being written. So the skill must say *when* to call, and also *which command*.

## What not calling costs

| Failure | How it shows | Cost |
| --- | --- | --- |
| Wrong signature, wrong overload, wrong package | compile error | one build plus one fix turn. Cheap **if** the build is fast and the next guess is right |
| Guessing again after a compile error | second and third compile error | a fix loop; the most expensive common case |
| Invented API (method or class that does not exist) | compile error, or the agent "fixes" it with another invention | a loop, or code the user has to throw away |
| Wrong version (API from another major version) | compile error or deprecation warning | a loop; the model's memory mixes versions |
| Silently wrong behaviour (returns null vs. throws, mutable vs. immutable, default charset or timezone, thread safety) | nothing at compile time; a test or production bug | the highest: nobody notices |

The compiler catches the first four, but only after the fact, and it does not say what the right API is. It never
catches the last one.

## Decision

**The build is the primary verifier for signatures. jdoc is for knowledge the model does not have, the step after
the first compile error, and behaviour the compiler cannot check.** Concretely:

Call jdoc when:

1. **Unfamiliar library or version.** Internal or company artifacts, niche libraries, and recent major versions whose
   API changed (the header shows the version the module actually resolves). Here the model's prior is weak and a
   guess is likely wrong. Looking up costs less than the loop.
2. **Discovery.** You need a class or method but do not know its exact name or package. Use `find` or `member`
   instead of inventing one.
3. **After a compile error on a library symbol** (`cannot find symbol`, `no suitable method found`,
   `method ... cannot be applied`, `incompatible types` involving a library type). Rule: **never guess twice**. The
   first error is cheap, a second blind guess is not.
4. **Behaviour matters and is not obvious.** Null handling, which exceptions are thrown, mutability, thread safety,
   defaults. Read the `method` docs first, then `source` only if the docs leave it open.
5. **Deprecated or changed API.** You suspect an API is deprecated, removed or changed between versions and you are
   about to rely on it.
6. **Instead of decompiling.** Whenever you would otherwise unzip a jar, read `~/.m2`, or run `javap`.

Do not call jdoc when:

- **Common JDK and ubiquitous APIs in their usual form**: `java.lang`, collections, streams, `Optional`, `java.time`
  basics, `java.nio.file.Files`/`Path`, and well-known parts of very common libraries. If you would bet on the
  signature, let the compiler confirm it.
- **The project already uses it.** If the same call already appears in the codebase, reading or grepping that code
  is cheaper and also shows the house style.
- **You already looked it up in this session.** The answer is in your context.
- **You are only reading code**, not writing calls (unless you need behaviour, see 4).
- **A compile is cheaper.** A fast single-module build and a guess you are confident about.

The rule "verify every unverified method" was dropped because it over-triggers. "Never call, let the compiler
decide" was rejected too: it costs most for unfamiliar libraries (repeated guesses), and it misses behaviour bugs.

## Cheapest sufficient command

| Need | Command | Not |
| --- | --- | --- |
| Name unknown | `find <part>` (substring, camel humps) | `package` on a guessed package |
| Method name known, class unknown | `member <name>` | `find` with many hits |
| One known method | `method 'Type#name(P1,P2)'` with params if you know the overload | `method Type#name` on a heavily overloaded method |
| Several members of one unfamiliar type | one `class Type` | several `method` calls |
| Implementations of an interface | `impls Type` | `find` |
| Behaviour the docs leave open | `source Type#name(...)` | `source` as a first step |
| Several unrelated known lookups | chain them in one shell call (`jdoc ... ; jdoc ...`) | one turn per lookup |
| Rest of a capped answer | `--page 2` only if the member you need is missing | always reading all pages |

Chaining saves turns. Each answer still starts with its own header line, and a miss says "no member ..." or "no type
...", so the output stays readable even though only the last exit code is visible.

## Harm-prevention rules kept

The exit-code contract (1 = not on this classpath, so do not invent the API; 2 = ambiguous, so pick the FQCN from the
imports; 3 = error, so show it to the user), no unzipping or decompiling by hand, public and protected API only,
`--from` for another module, `[version unresolved]` and `[decompiled, no docs]` markers, paging, and
`index --force` only for stale answers. These cost a few lines and prevent the worst outcomes. They stay.

## Description line

opencode lists only `name` and `description` (max 1,024 characters) in the `skill` tool's `<available_skills>`
section. The body loads only when the agent calls `skill({name: "jdoc"})`. So the description has to name the
situations that should load the skill (unfamiliar library or version, compile error on a library call, behaviour
question, urge to decompile). It also says plainly that common JDK calls do not need it, so the skill does not load on
every Java edit. The body stays compact (about 70 lines), because it costs tokens every time it loads.

## AGENTS.md snippet

The snippet is for sessions where the skill is not loaded. It repeats the same triggers in three lines, plus "never
decompile", and points to `jdoc --help`. It must never be stricter than the skill. Otherwise the two would contradict
each other and the stricter one would win.
