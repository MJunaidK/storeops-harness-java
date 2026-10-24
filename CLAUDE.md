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
4. Changes → start **planner** run 1 again with the requested changes; status stays
   `AWAITING APPROVAL`; return to step 3.
5. `APPROVED` → start **planner** run 2. It sets `STATUS: APPROVED` and writes one
   `.harness/output/sprint-<N>-contract.md` per sprint.
6. For each sprint in order: **generator** → **evaluator**, repeated per the routing rules below.
7. After each sprint's final verdict (or escalation): archive that sprint's `generator-summary.md` and
   `evaluator-feedback.md` to `.harness/reviews/` as `sprint-<N>-generator-summary.md` and
   `sprint-<N>-evaluator-feedback.md`, then start **monitor**.
8. After the last sprint: tell the developer the run is complete and list the files in `.harness/reviews/`.

Never start the Generator while `spec.md` does not begin with `STATUS: APPROVED`.

## Routing, iteration limit, escalation ← TODO (Phase 4)

## Context strategy ← TODO

## CI/CD relationship ← TODO
