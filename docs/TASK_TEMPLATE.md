# <Title: what changes, in a few words>

Issue: #N

<!-- Template for a task brief, a local file `tasks/<N>-<slug>.md` (ignored by git), named after the GitHub issue
     #N it works on; the issue number is the task's only ID. A brief says what and why, never how in code: no code, no class bodies.
     Keep it to one screen where possible. Delete the comments and any section that does not apply. -->

**Owns:** <packages (main and test), or files for non-Java work, e.g. `lookup`, `dist/install.sh`, `InstallerIT`>.
<!-- Briefs in the same wave must own disjoint files. Name shared hot spots (schema version, pom.xml) explicitly.
     A shared doc section (SPEC, ARCHITECTURE, README) counts as a file: at most one brief per wave owns it. -->
**Depends on:** <#N, #M> (read their notes `tasks/notes/<N>.md`, ...).
**Runs in parallel with:** <#K, or "alone"> <!-- and what this brief must not touch because of it -->
**Read first:** `docs/ARCHITECTURE.md`, the `package-info.java` of the packages it touches, `docs/SPEC.md` "<section>".

## Why

<The problem or gap, with evidence: a failing case, a measurement, an output that is wrong. Two to five lines.>

## Goal

<The outcome in one to three sentences, observable from outside (output, exit code, timing, file).>
<!-- The Goal is the task's boundary; Scope lists the known changes, not the limit. State a clean-up as an end state
     ("no public member in packages X, Y that main code does not use"), not as a list of what was found. -->

## Scope

1. **<Change>.** <What must be true afterwards, edge cases, what must stay unchanged.>
2. **<Change>.** ...

Out of scope: <tempting neighbouring changes that belong to another task>.

## Binding decisions

<!-- Decisions the orchestrator or maintainer already made; the agent follows them and does not reopen them.
     Say which ones change the spec, so the agent reports them under Doc impact. -->
- <e.g. bump the index schema version once; existing indexes are rebuilt>
- <e.g. no new dependency; no change to the output format of other commands>

## Tests

- Unit: <the classes and cases: normal, edge, error>.
- Integration: <which IT is new or extended, and what it proves>.
- Expected outputs that change: <EndToEndIT, app tests, ...; list them in the notes>.
<!-- Tests needing an external tool use testsupport.Prerequisites (skip locally, fail in CI). -->

## Done when

- `./mvnw -B verify -Djdoc.buildDir=target/<N>` passes with no new skipped tests.
- <the observable check, e.g. before/after output of one command, a measurement, a CI run on a draft PR>.
- Notes in `tasks/notes/<N>.md` (see `docs/AGENT_GUIDE.md`, "When you finish"), with a proposed commit split.
- Docs: package-info/Javadoc of the owned packages; <shared doc sections this brief owns, or "SPEC/ARCHITECTURE/README:
  report under Doc impact">.

## Rules

Follow `docs/AGENT_GUIDE.md`. <Task-specific rules: may push a throwaway branch with a draft PR to test CI;
do not tag; ask before ...>
