# Run log — Sprint 2

Final verdict: PASS · Iterations used: 1 of 3 · Escalated: no
Sources: .harness/reviews/sprint-2-iter-1-generator-summary.md, .harness/reviews/sprint-2-iter-1-evaluator-feedback.md, .harness/reviews/usage-log.md (sprint 2 rows: none recorded), .harness/reviews/sprint-1-run-log.md (earlier run log), .harness/skills/app-context/SKILL.md. No sprint-2-escalation.md exists.

## Iterations

| Iter | Verdict | Failed checks (ID, severity) | Scores Arch/Func/Qual/Total | Tests run | Generator / Evaluator time |
| ---- | ------- | ---------------------------- | --------------------------- | --------- | -------------------------- |
| 1    | PASS    | none                         | 100/100/100/100             | 96        | 7m 23s / 2m 30s            |

## Cost

| Iter | Agent     | Tokens | Duration           | Source (orchestrator / usage-log)                                             |
| ---- | --------- | ------ | ------------------ | ----------------------------------------------------------------------------- |
| 1    | generator | 63040  | 7m 23s (443138 ms) | orchestrator (tokens and duration as reported); usage-log has no sprint 2 row |
| 1    | evaluator | 48104  | 2m 30s (150111 ms) | orchestrator (tokens and duration as reported); usage-log has no sprint 2 row |
| —    | planner   | n/r    | n/r                | orchestrator (planner runs for this sprint reported as n/r)                   |

Estimated cost: 111144 tokens (estimate: sum of the generator and evaluator tokens above; planner and monitor n/r); weekly-allowance change: n/r (usage-log has no sprint 2 rows).

## Conditions carried (CONDITIONAL PASS only)

none

## Quality trend

- Iter 1 (PASS): no check failed, so there were no per-iteration fixes to track. Evaluator note only (no effect on verdict): `ActivityService.isAllowedTransition` keeps a DONE/BLOCKED target check that is unreachable from the bulk path; the Generator disclosed it as a known gap, outside the contract.
- Compared with earlier run logs (sprint-1-run-log.md): final total score 100 in both sprints, so no score change (0). Sprint 1 failed SC-2 (B) and TQ-4 (C) in iter 1; neither check ID fails in sprint 2, so no repeated failure.

## Skill-file signals

| Signal | Evidence (sprint, iter, check ID, file:line) | Skill file to review |
| ------ | -------------------------------------------- | -------------------- |

none (S1: no check failed in any iteration; S2: no check failed in this sprint; S3: 1 iteration, PASS, not escalated; S4: no E2 or E3 occurred, the verdict line was valid)

## Developer notes

- Run in accept-edits mode: file writes auto-approved; every git and `./mvnw verify` command approved by hand.
- The orchestrator's broad `git add .harness/reviews` (CLAUDE.md edit missed) picked up only this sprint's files.
- Weekly-allowance change is n/r here by design: the Monitor runs before usage-log rows are filled; see usage-log.md.
