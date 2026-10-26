# Reflection — StoreOps harness demonstration run

**Run:** _shift handover bulk update_, 3 sprints, 4 iterations (Sprint 1: FAIL → PASS; Sprints 2 and 3: PASS first time),
114 tests, all merged to `main`, ≈ 4–5 % of a weekly Pro allowance (≈ $3.50 recorded, `.harness/reviews/usage-log.md`).

## What the harness did well

- **It caught a real defect that a status-code review would have passed.** In Sprint 1 the Generator added a test asserting
  that a null `updates` list returns 200 — behaviour Sprint 2 had to reject. Every build gate was green; the Evaluator still
  returned FAIL on SC-2 (`ActivityServiceTest.java:331`), traced it to spec DEC-2, and iteration 2 fixed it without me.
- **Its verdicts were repeatable.** Two independent evaluations of the same code (before and after removing a leaked example,
  D14) gave the same verdict, findings and scores. My own unwritten judgement had said CONDITIONAL PASS — the written severity
  rule is what made the result reproducible (D12, D13).
- **The loop ran autonomously once the rules settled.** Sprints 2 and 3 went from branch to merged run log without a
  decision from me, in 30 and 19 minutes. The rules also shaped design: under D15 the Generator kept a 2-argument
  constructor so protected tests compiled unchanged.

## Where it fell short

1. **Instructions did not stop a habit; I did.** All three acting agents at least once ran a command that hides its exit
   code — the Generator `./mvnw -q verify | tail; echo $?` (a failed build would report exit 0), the Evaluator
   `./mvnw verify | grep | head`, the orchestrator `git merge main | tail`. Each was written into the agent's _Never_ list,
   and each was stopped only because I rejected it in a permission prompt. The safety of an "autonomous" loop rested on a
   human clicking _No_.
2. **A PASS can rest on thin evidence, and nothing notices.** In Sprints 2 and 3, TQ-4 and SC-2 passed with "none found",
   and AC-3.4's three refused roles were not named. I verified by hand (`ActivityControllerTest.java:489–496`): the code was
   correct, the proof was missing. The Monitor cannot see this — its signals fire only on FAILs.
3. **Harness files drift, and nothing checks them.** The approved contract named a method (`bulkUpdateStatus`) that broke
   the D7 write-prefix rule its own text claimed to keep; a commit message said CLAUDE.md "adds exact files" when the edit was
   missing, so the orchestrator used a broad `git add`. Both were caught by people, not the harness.
4. **Two designed paths were never exercised:** escalation (E1–E3) and D15's "unless the contract allows it" exception.

## One concrete improvement: enforce command rules in the tool, not the prompt

Add a project permission list in `.claude/settings.json`: **allow** exactly `./mvnw verify`, `git status`, `git diff`,
`git log`, `git add`, `git commit`, `git switch`, `git merge`; **deny** `git push`, `git reset`, `git rebase` and any
`-DskipTests` / `-Djacoco.skip`. Anything not on the list — including a piped `verify` — falls back to a prompt instead of
running, so the loop can run without per-command approvals and a dangerous variant still cannot slip through. Verify it
the way the gates were verified: sabotage — ask an agent to run `./mvnw verify | tail` and `git push`, and confirm both are
blocked.

**Link to the Design Brief.** This extends the reasoning of **D11** from agents to commands: D11 chose subagent wrappers so
that tool limits are _enforced by Claude Code_ rather than requested in text. **D16** assumed the main session would be
"approved only for specific git commands" — in the run that assumption held only because I was watching. The permission
list makes D16's assumption structural.

_Next after that:_ tighten the evidence rule (TQ-4 must list the test classes scanned; SC-2 must name each Scope-out item
checked) and add a Monitor signal for PASS rows whose evidence names no file or test.
