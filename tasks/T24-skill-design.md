# T24 — Rework the skill: when is jdoc actually worth calling?

**Owns:** `dist/skill/SKILL.md`, `dist/AGENTS-snippet.md`, a new `docs/SKILL-DESIGN.md`. Read-only everywhere else.
**Depends on:** T18 (which wrote the first version of both files and checked them against `--help`; read its notes).
**Spec:** `OUTLINE.md` "Goal and decisions", "Skill and AGENTS.md", "Commands and output";
`docs/STARTUP-RESEARCH.md` "Measured" (call latency); the real `jdoc --help`.

## Problem
The current skill tells the agent to call jdoc "before calling any library method you have not verified in this
session". Taken literally that means a jdoc call before almost every line touching a library or the JDK: many
extra tool calls, tokens and turns, most of them for APIs the model already knows well (`List.of`,
`String.isBlank`, `Optional.orElseThrow`). Over-triggering wastes the agent's context and the user's time;
under-triggering lets the agent guess. The skill must strike the balance deliberately.

## Goal
Decide, with written reasoning, when an agent should call jdoc and which command it should use, then rewrite the
skill and the AGENTS.md snippet accordingly.

## Scope
1. **Reason about cost vs. benefit** and write it down in `docs/SKILL-DESIGN.md` (concise, ≤ ~2 pages):
   - Cost of a call: wall time (see measured numbers), tokens of the answer (output cap ~2,000 tokens/page), one
     extra agent turn.
   - Cost of not calling: wrong signature → compile error → fix loop; silently wrong behaviour (nulls, exceptions,
     defaults); invented APIs; wrong version.
   - Signals that make a lookup worth it, e.g.: library or version the model is unlikely to know well (niche,
     internal/company libraries, recent major versions); unfamiliar or ambiguous type names; overloads with
     non-obvious parameters; behaviour questions (null handling, exceptions, thread safety); after a compile error
     such as "cannot find symbol" or "no suitable method"; before relying on a deprecated or recently changed API;
     when the user's code uses the library in a way the model does not recognise.
   - Signals that make it not worth it: core JDK and ubiquitous APIs used in their common form; APIs already
     verified or already used correctly elsewhere in the same codebase (reading the project's own code is cheaper);
     cases where compiling is the cheaper check.
   - Choosing the cheapest sufficient command: `find`/`member` for discovery, one `class` instead of several
     `method` calls when exploring a type, `method` with params for one overload, `source` only for behaviour
     questions; paging only when needed.
   - Consider whether the build itself (compile errors) should be the primary verifier, with jdoc as the next
     step, and decide.
2. **Skill description (frontmatter):** opencode keeps only the description in context permanently. Make it short and
   precise so the skill loads in the right situations and not on every Java edit. Check opencode's skill format and
   loading behaviour (web search allowed) and follow it.
3. **Skill body:** rewrite "When to call it" around the decided triggers, keep the rules that prevent harm (exit
   codes, never invent an API on exit 1, never unzip jars or decompile yourself, `--from` for other modules,
   `[version unresolved]`, paging), and add short worked examples (2–4) of good and of unnecessary calls.
   Keep the body compact; it costs tokens every time the skill loads.
4. **AGENTS.md snippet:** align with the new triggers (it must not contradict the skill and should stay a few lines).
5. **Consistency:** every command, flag, exit code and output marker in the files must match the real CLI
   (`java -jar target/jdoc.jar --help` and per-command help). `scripts/smoke-test.sh` or T18's checks must still pass if
   they reference these files.

## Done when
`docs/SKILL-DESIGN.md` explains the decision; `SKILL.md` and `AGENTS-snippet.md` implement it; notes list the
triggers chosen, what was dropped from the old version and why, and any spec-level change for `OUTLINE.md` (the
orchestrator updates the outline's skill section).
