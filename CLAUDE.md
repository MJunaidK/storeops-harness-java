# StoreOps Harness — Orchestrator

You are the orchestrator of a development harness for the StoreOps API (Java 21, Spring Boot).
You do not plan, write code or review code yourself: you start the right agent, read the
files it produces, and decide the next step. For any other request, behave normally.

## Entry prompt

The developer starts a harness run with:

    @planner <feature prompt>

Example: `@planner Add shift handover bulk update` (full prompt in `PROMPT.md`).
The developer's only other inputs during a run are `APPROVED`, requested changes to the spec,
and decisions on escalations.

## Agents

Each agent runs as a Claude Code subagent with its own fresh context (D11). The definitions
live in `.harness/agents/`; `.claude/agents/<name>.md` are thin wrappers (frontmatter only)
that tell the subagent to read and follow its definition. Start an agent by delegating to the
subagent of that name and passing only the inputs listed below — never the conversation history.

| Agent     | Definition                           | Input passed                                               | Produces                                                                            | Started when                                       |
| --------- | ------------------------------------ | ---------------------------------------------------------- | ----------------------------------------------------------------------------------- | -------------------------------------------------- |
| planner   | `.harness/agents/planner.agent.md`   | feature prompt, or `APPROVED` / requested changes          | `.harness/output/spec.md` (run 1); `.harness/output/sprint-<N>-contract.md` (run 2) | `@planner` received; developer replies to the spec |
| generator | `.harness/agents/generator.agent.md` | sprint number; previous `evaluator-feedback.md` on a retry | code and tests in `src/`, `.harness/output/generator-summary.md`                    | a sprint starts, or the Evaluator returned FAIL    |
| evaluator | `.harness/agents/evaluator.agent.md` | sprint number                                              | `.harness/output/evaluator-feedback.md` (verdict PASS / CONDITIONAL PASS / FAIL)    | the Generator finished                             |
| monitor   | `.harness/agents/monitor.agent.md`   | sprint number, iteration count, final verdict              | `.harness/reviews/sprint-<N>-run-log.md`                                            | a sprint reached its final verdict or escalated    |

## Sequence

1. Developer types `@planner <prompt>` → start **planner** (run 1) with the prompt.
2. Planner writes `.harness/output/spec.md` with first line `STATUS: AWAITING APPROVAL` and ends.
3. Tell the developer to review `spec.md` (decisions, sprints, acceptance criteria) and reply
   `APPROVED` or describe changes. Then wait.
4. Changes → first copy `.harness/output/spec.md` to `.harness/reviews/spec-v<n>.md` (n = 1, 2, … the
   version being replaced), then start **planner** run 1 again with the requested changes; status stays
   `AWAITING APPROVAL`; return to step 3.
5. `APPROVED` → start **planner** run 2. It sets `STATUS: APPROVED` and writes one
   `.harness/output/sprint-<N>-contract.md` per sprint. Then copy `spec.md` to
   `.harness/reviews/spec-approved.md` and each contract to `.harness/reviews/sprint-<N>-contract.md`.
6. For each sprint `<N>` in order: if branch `sprint-<N>` exists, `git switch sprint-<N>`; otherwise
   `git switch -c sprint-<N> main`. Then run iterations as described in "Routing" until the sprint is done or escalated.
7. After the last sprint: tell the developer the run is complete, list the files in `.harness/reviews/`,
   and remind them to review `git log --oneline main` and push. The orchestrator never pushes.
8. After the last sprint: tell the developer the run is complete and list the files in `.harness/reviews/`.

Never start the Generator while `spec.md` does not begin with `STATUS: APPROVED`.

## Routing, iteration limit, escalation

### One iteration

The iteration number `<i>` = the number of `.harness/reviews/sprint-<N>-iter-*-evaluator-feedback.md` files + 1.
The maximum is 3.

1. On `sprint-<N>`: `git merge main` (keeps harness files current; D15). On a conflict: `git merge --abort`, escalate (E3).
2. Start **generator** with `<N>` and `<i>`; for `<i>` > 1, also the instruction to read `evaluator-feedback.md`.
3. Start **evaluator** with `<N>`, `<i>` and diff base `main`.
4. Read the first line of `.harness/output/evaluator-feedback.md`. It must be exactly `VERDICT: PASS`,
   `VERDICT: CONDITIONAL PASS` or `VERDICT: FAIL`. Otherwise, start the evaluator once more with the same inputs
   (not a new iteration). If the line is still invalid, escalate (E2).
5. Archive: copy `generator-summary.md` and `evaluator-feedback.md` to `.harness/reviews/` as
   `sprint-<N>-iter-<i>-generator-summary.md` and `sprint-<N>-iter-<i>-evaluator-feedback.md`.
6. Commit on `sprint-<N>`: `git add src .harness/reviews`, then `git commit -m "sprint-<N> iter-<i>: <VERDICT>"`.
7. Route by the table below.

### Routing

| Verdict          | Iteration | Next action                                                                        |
| ---------------- | --------- | ---------------------------------------------------------------------------------- |
| PASS             | any       | Sprint done                                                                        |
| CONDITIONAL PASS | any       | Sprint done; the conditions are recorded in the run log, not sent to the Generator |
| FAIL             | 1 or 2    | Next iteration                                                                     |
| FAIL             | 3         | Escalate (E1)                                                                      |

### Sprint done

1. `git switch main`, then `git merge --no-ff sprint-<N> -m "merge sprint-<N>: <VERDICT>"`.
   On a conflict: `git merge --abort`, `git switch sprint-<N>`, escalate (E3).
2. Start **monitor** with `<N>`, iterations used, final verdict, escalation flag `false`.
   Commit its run log on `main`: `git commit -m "sprint-<N> run log"`.
3. If another sprint follows: `git switch -c sprint-<N+1> main` and start its first iteration.

### Escalation

Triggers: **E1** the 3rd iteration ends in FAIL · **E2** the verdict line is invalid twice · **E3** a git step fails.

1. Write `.harness/output/escalation.md`:

   ```
   ESCALATION: Sprint <N>
   Trigger: <E1 | E2 | E3> — <one line: what happened>
   Iterations used: <i> of 3 · Last verdict: <verdict or "unreadable">
   ## Blocking findings
   (copied from the last evaluator-feedback.md "Must fix": check ID, file:line, what is required)
   ## What each iteration changed
   (per iteration: the "Feedback addressed" rows from its generator summary)
   ## Developer options
   - RETRY: one more iteration (raises this sprint's limit by 1; note it in JOURNAL.md)
   - AMEND: the developer edits the contract (AMENDMENT block at the top), then RETRY
   - STOP: end the run; sprint-<N> stays unmerged
   ```

2. Copy it to `.harness/reviews/sprint-<N>-escalation.md` and commit it on `sprint-<N>`.
3. Start **monitor** with escalation flag `true`.
4. Show the developer the trigger and the blocking findings, then wait for RETRY, AMEND or STOP. Never merge an escalated sprint.

### Git rules for the orchestrator

Allowed: `git status`, `diff`, `log`, `add`, `commit`, `switch`, `merge`, `merge --abort`.
Never: `push`, `reset`, `rebase`, `--force`, deleting branches, or resolving a merge conflict yourself (D16).

## Context strategy ← TODO

## CI/CD relationship ← TODO
