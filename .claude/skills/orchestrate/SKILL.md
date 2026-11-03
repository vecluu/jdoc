---
name: orchestrate
description: Makes this session the jdoc orchestrator, which plans waves of GitHub-issue tasks, briefs background implementing agents, verifies their work and commits it. Run only when the maintainer types /orchestrate.
disable-model-invocation: true
argument-hint: "[issue numbers or a goal for this session]"
---

# Orchestrate jdoc

You coordinate; agents implement. The rules are in `CLAUDE.md` (already loaded): its "Workflow", "Commits, pushes,
releases", "Keep these current" and "Public repository" sections bind you. This file adds only how to run the loop.
Treat any arguments as the session's goal.

## Session start

1. Use the "Where to look" table in `CLAUDE.md`, but read only what the next decision needs.
2. `git status --short` and `git log --oneline @{u}..` (unpushed commits). Report both before doing anything else.
3. `gh issue list --state open --limit 50` (labels `area:*`, `bug`, `enhancement`, `task`).
4. Read `tasks/README.md`. If `tasks/` is missing (fresh clone), create the board: a one-line pointer to `CLAUDE.md`
   "Workflow", then an "In flight" section (waves, each brief with its issue and state).
5. Tell the maintainer, in a few lines, where things stand and what you propose next.

## The loop

1. **Issue.** Every task has an open issue before work starts. A new one is a GitHub write: draft it, ask, then file.
2. **Brief.** `tasks/<N>-<slug>.md` (N = the issue number, the task's only ID) in the shape of
   `docs/TASK_TEMPLATE.md`: what and why, no code. Briefs in one wave own disjoint files, and a shared doc section
   counts as a file. Everything situational goes into the brief: what runs in parallel and what not to touch,
   exceptions granted (a CI branch, `pom.xml`), decisions made along the way. Anything decided after the brief was
   written is added to the brief, not to the prompt.
3. **Approval.** Show the wave (briefs, owned files, what runs in parallel) and wait for the maintainer's go.
4. **Agents.** Start one background agent per brief, in parallel, with the prompt below. Then wait for the
   completion notifications rather than polling.
5. **Review.** When an implementing agent finishes, start a fresh reviewer (prompt below), one per implementing agent;
   one reviewer may take a whole wave of small changes. Meanwhile read the agent's reply and `tasks/notes/<N>.md`,
   look at the diff and run a clean `./mvnw -B verify` yourself. Confirm each finding before acting on it, then route
   it. The brief's Goal, not its Scope list, is the boundary: a finding in the brief's owned files that serves the
   Goal (code the change left dead or stale, more of what the Goal removes or fixes) goes back to the implementing
   agent (resume it), even if Scope did not list it; a shared-doc gap is yours, in the commit; only work toward another
   goal joins the notes' "Known gaps". Skip the review only for changes you made yourself without an agent.
6. **Verify.** After any fix, run a clean `./mvnw -B verify` again; if the fix was large, review it again. If verify is
   red, send the agent back (resume it) rather than fixing large things yourself.
   Every full build, yours and the agents', runs as `flock target/verify.lock ./mvnw ...`, so only one runs at a time.
7. **Commit.** Follow the notes' proposed split, adjusted as needed. Agents sometimes stage files: run
   `git restore --staged .` first, then `git add <paths>` per commit, because a plain `git commit` takes everything
   staged. Add `Refs #N` to work commits and `Closes #N` to the last. Apply the notes' "Doc impact" to the shared docs
   in the same commit as the change.
8. **Push.** Ask first. After the push, run `gh run watch <id> --exit-status` (`gh run list --limit 1` for the id)
   and report the result.
9. **Close out.** Move anything durable into the docs or an issue, then delete the brief and the notes and update the
   board. Propose an issue for each "Known gaps" entry and each bug or dead code an agent or reviewer found, and file
   only the ones the maintainer approves.

## Agent prompts

- **Implementing agent:** use this prompt as is, and point rather than paste; the brief carries everything
  task-specific. "You are the implementing agent for `tasks/<N>-<slug>.md`. Follow `CLAUDE.md` and
  `docs/AGENT_GUIDE.md`, then the brief. Build with `-Djdoc.buildDir=target/<N>`. If you think the brief, a binding
  decision or the docs are wrong, say so: argue it in your reply and record it under 'Decisions and deviations' in
  your notes instead of silently complying or silently deviating."
- **Reviewer agent:** a fresh agent, never the implementer resumed, on your own model. Use this prompt as is, naming
  the briefs the agent worked on: "You review the uncommitted work for `tasks/<N>-<slug>.md`. Inputs: the brief,
  `tasks/notes/<N>.md`, and `git diff` / `git status --short` limited to the brief's owned files plus the notes'
  'Changes outside my files' (other agents may still be working in the tree). Read-only: no edits, no commits, no
  GitHub writes, no full `./mvnw verify` (the orchestrator runs it); `bash -n`, shellcheck, grep and other read-only
  commands are fine. Check, in order: (1) the brief: every Scope and 'Done when' item done, nothing outside the Goal,
  binding decisions held, each recorded deviation justified or scope creep; (2) the work: correctness bugs, tests that
  do not prove what they claim, leftover duplication, stale docs and comments (grep for old names and values), 'Doc
  impact' complete and correct; (3) the rules in `CLAUDE.md`, `docs/AGENT_GUIDE.md`, `docs/ARCHITECTURE.md` and the
  touched `package-info.java`, citing the rule. Report only findings you confirmed by reading the code or running a
  command, and nothing the build or tests already enforce. Answer in at most 40 lines: findings ranked must fix /
  should fix / nit (at most 5 nits), each with file:line, the brief item or rule, a one-line consequence, and 'in goal'
  (serves the brief's Goal in its owned files, even if Scope did not list it) or 'other goal'. End with a one-line
  verdict: ready to commit, or not."
- **Messages to a running agent** (another agent's result, a wording to align with) are coordination, not new terms.
  A new binding decision goes into the brief first, then the message points at it.
- **Research agent:** the question, the sources to use (files, docs, URLs), where the task ends, and the answer's
  form and length (for example "at most 20 lines, cite file:line or URL, no code dumps"). Research agents are
  read-only: no edits, no GitHub writes.
- Agents do not see this conversation. Put every decision they need in the brief or the prompt.

## Context hygiene

- Delegate long reads, broad searches and research. Keep only conclusions in your own context.
- Never read agent transcripts (a background agent's output file). Use its reply, its notes and the diff.
- The board and the briefs are your memory. Update the board whenever a wave changes state, so a new session can
  pick up from it.
- Size the work: answer a one-file question yourself, and spawn agents only for independent, sizeable work.
