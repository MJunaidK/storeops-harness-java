# Usage log

Numbers copied by hand from the Claude Code CLI (per-agent tokens are visible only while the agent runs).
Plan: Pro. Model: Sonnet for all agents (D3). "n/r" = not recorded.

| Date       | Sprint | Iter | Agent     | Tokens | Duration | Notes                                                         |
| ---------- | ------ | ---- | --------- | ------ | -------- | ------------------------------------------------------------- |
| 2026-10-24 | 1      | 1    | generator | n/r    | 19m 47s  | from JOURNAL                                                  |
| 2026-10-25 | 1      | 1    | evaluator | n/r    | —        | leaked run (D14), discarded                                   |
| 2026-10-25 | 1      | 1    | evaluator | n/r    | 7m 14s   | clean re-run (D14)                                            |
| 2026-10-25 | 1      | 2    | generator | n/r    | —        | aborted after ~3 min (auto mode); edits discarded             |
| 2026-10-25 | 1      | 2    | generator | n/r    | 5m 52s   | via orchestrator                                              |
| 2026-10-25 | 1      | 2    | evaluator | n/r    | 12m 47s  | includes waiting on manual approvals and one rejected command |

## Per run (from /usage)

| Date       | Run                              | Session % before → after                | Weekly % before → after                | Total cost (API equiv.)                      | Wall time                     |
| ---------- | -------------------------------- | --------------------------------------- | -------------------------------------- | -------------------------------------------- | ----------------------------- |
| 2026-10-24 | Sprint 1 iter 1 (generator only) | n/r → ≈ 7%                              | 21% → 22%                              | $0.86                                        | ~20 min                       |
| 2026-10-25 | Sprint 1 iter 2 (orchestrator)   | 15% → n/r (window reset before reading) | 28% → 30% (upper bound, includes chat) | $0.74 (session total, Sonnet; Haiku < $0.01) | ≈ 45 min active (19:28–20:14) |

Notes:

- 2026-10-26 08:58, last-24h subagent share: evaluator 47%, generator 22%. With a small fix (3 changes),
  the Evaluator (own `./mvnw verify` + full checklist) cost about twice the Generator. Evaluator cost is
  roughly fixed per iteration; Generator cost depends on how much there is to fix.
- Unclear whether the session's "Total cost" includes subagent usage (API duration shows only 1m 35s,
  while the agents ran ~19 min). Treat $0.74 as a lower bound for the run.
