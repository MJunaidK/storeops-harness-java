# Run log — Sprint 1
Final verdict: PASS · Iterations used: 2 of 3 · Escalated: no
Sources: .harness/reviews/sprint-1-iter-1-generator-summary.md, .harness/reviews/sprint-1-iter-1-evaluator-feedback.md, .harness/reviews/sprint-1-iter-2-generator-summary.md, .harness/reviews/sprint-1-iter-2-evaluator-feedback.md, .harness/reviews/usage-log.md (sprint 1 rows), .harness/skills/app-context/SKILL.md. No escalation file exists; no earlier run logs exist (first sprint). sprint-1-iter-1-evaluator-feedback-LEAKED.md was not read (excluded by rule).

## Iterations
| Iter | Verdict | Failed checks (ID, severity) | Scores Arch/Func/Qual/Total | Tests run | Generator / Evaluator time |
|---|---|---|---|---|---|
| 1 | FAIL | SC-2 (B), TQ-4 (C) | 100/82/100/93 | 78 | 19m 47s / 7m 14s |
| 2 | PASS | none | 100/100/100/100 | 77 | 5m 52s / 12m 47s |

## Cost
| Iter | Agent | Tokens | Duration | Source (orchestrator / usage-log) |
|---|---|---|---|---|
| 1 | generator | n/r | 19m 47s | orchestrator (duration); usage-log (tokens n/r) |
| 1 | evaluator | n/r | 7m 14s | orchestrator (duration); usage-log (tokens n/r). Leaked first run discarded per D14, not counted |
| 2 | generator | n/r | 5m 52s | orchestrator (duration); usage-log (tokens n/r) |
| 2 | evaluator | n/r | 12m 47s | orchestrator (duration); usage-log (tokens n/r) |

Estimated cost: n/r (no token counts recorded for any agent run); weekly-allowance change: usage-log records 21% → 22% for iter 1 generator, and 28% → 30% (upper bound, includes chat) for iter 2; the sprint 1 monitor row in usage-log still holds placeholders (`<after>`), so the total change is n/r.

## Conditions carried (CONDITIONAL PASS only)
none

## Quality trend
- Iter 1 (FAIL): SC-2 (B) failed at ActivityServiceTest.java:331 (a test asserted null `updates` gives an empty success, which belongs to Sprint 2). TQ-4 (C) failed at ActivityServiceTest.java:314 (a tautological `updatedAt` assertion). Both were fixed in iter 2: the null-list test and the normalisation in BulkStatusRequest.java:8 were removed, and the tautological assertion was removed. Architecture and Quality stayed at 100 in both iterations; the iter 1 Functional score (82) is the only score below 100.
- Iter 2 (PASS): no failed checks. Evaluator informational note only (no effect on verdict): the test name `bulkUpdateToCurrentStatusIsConflictAndLeavesUpdatedAtUnchanged` no longer asserts anything about `updatedAt`.
- Compared with earlier run logs: no earlier run logs exist, so no score change or earlier-sprint check failures can be compared. This is the baseline run for the trend.

## Skill-file signals
| Signal | Evidence (sprint, iter, check ID, file:line) | Skill file to review |
|---|---|---|
none (S1: no check failed in 2 or more iterations; S2: no earlier run log; S3: 2 iterations, PASS, not escalated; S4: no E2 or E3 occurred, both verdict lines were valid)

## Developer notes
