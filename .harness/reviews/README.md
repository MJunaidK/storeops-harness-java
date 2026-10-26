# Review archive — demonstration run "shift handover bulk update"

Committed by the orchestrator after every iteration (D16). Reading order per sprint:
contract → generator summary → evaluator feedback → run log.

| PDF artefact                         | File(s) here                                                                                         |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| spec.md (as produced by the Planner) | `spec-approved.md` (final); drafts `spec-v1.md`, `spec-v2.md`                                        |
| sprint contracts                     | `sprint-1-contract.md`, `sprint-2-contract.md`, `sprint-3-contract.md` (amendment blocks at the top) |
| generator-summary.md                 | `sprint-<N>-iter-<i>-generator-summary.md` — one per iteration                                       |
| evaluator-feedback.md                | `sprint-<N>-iter-<i>-evaluator-feedback.md` — one per iteration, FAIL included                       |
| run-log.md                           | `sprint-1-run-log.md`, `sprint-2-run-log.md`, `sprint-3-run-log.md`                                  |
| token / cost record                  | `usage-log.md`                                                                                       |

| Sprint | Iterations | Final verdict                  | Files                                                           |
| ------ | ---------- | ------------------------------ | --------------------------------------------------------------- |
| 1      | 2          | PASS (iter 1 FAIL: SC-2, TQ-4) | `sprint-1-iter-1-*`, `sprint-1-iter-2-*`, `sprint-1-run-log.md` |
| 2      | 1          | PASS                           | `sprint-2-iter-1-*`, `sprint-2-run-log.md`                      |
| 3      | 1          | PASS                           | `sprint-3-iter-1-*`, `sprint-3-run-log.md`                      |

Notes:

- `sprint-1-iter-1-evaluator-feedback-LEAKED.md` is a discarded evaluation whose worked example leaked the
  answers (D14); kept as evidence, not counted.
- No escalation occurred.
- `.harness/output/` holds the working copies during a run and is gitignored (PDF §7); every file the agents
  produce there is archived here.
- Tests live in `src/test/java` (Maven convention), mirroring the module structure of `src/main/java`.
