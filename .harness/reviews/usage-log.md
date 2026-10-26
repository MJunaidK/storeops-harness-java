# Usage log

Numbers copied by hand from the Claude Code CLI (per-agent tokens are visible only while the agent runs).
Plan: Pro. Models: Sonnet for planner, generator, evaluator and the orchestrator; Haiku for monitor (D3, D18).
"n/r" = not recorded.

| Date       | Sprint | Iter | Agent     | Tokens                                                           | Duration | Notes                                                                                                                      |
| ---------- | ------ | ---- | --------- | ---------------------------------------------------------------- | -------- | -------------------------------------------------------------------------------------------------------------------------- |
| 2026-10-24 | 1      | 1    | generator | n/r                                                              | 19m 47s  | from JOURNAL                                                                                                               |
| 2026-10-25 | 1      | 1    | evaluator | n/r                                                              | —        | leaked run (D14), discarded                                                                                                |
| 2026-10-25 | 1      | 1    | evaluator | n/r                                                              | 7m 14s   | clean re-run (D14)                                                                                                         |
| 2026-10-25 | 1      | 2    | generator | n/r                                                              | —        | aborted after ~3 min (auto mode); edits discarded                                                                          |
| 2026-10-25 | 1      | 2    | generator | n/r                                                              | 5m 52s   | via orchestrator                                                                                                           |
| 2026-10-25 | 1      | 2    | evaluator | n/r                                                              | 12m 47s  | includes waiting on manual approvals and one rejected command                                                              |
| 2026-10-26 | 1      | –    | monitor   | ≈ 118k (11.4k in, 5.1k out, 72.9k cache read, 29.1k cache write) | 4m 54s   | Haiku; run by hand after the sprint; tokens from the /usage Haiku line ($0.0081); duration as reported to the orchestrator |

## Per run (from /usage)

| Date       | Run                                   | Session % before → after                | Weekly % before → after                | Total cost (API equiv.)                                    | Wall time                        |
| ---------- | ------------------------------------- | --------------------------------------- | -------------------------------------- | ---------------------------------------------------------- | -------------------------------- |
| 2026-10-24 | Sprint 1 iter 1 (generator only)      | n/r → ≈ 7%                              | 21% → 22%                              | $0.86                                                      | ~20 min                          |
| 2026-10-25 | Sprint 1 iter 2 (orchestrator)        | 15% → n/r (window reset before reading) | 28% → 30% (upper bound, includes chat) | $0.74 (session total, Sonnet; Haiku < $0.01)               | ≈ 45 min active (19:28–20:14)    |
| 2026-10-26 | Sprint 1 monitor (Haiku, retroactive) | 8% → 11%                                | 30% → 31% (includes chat)              | $0.138 (monitor Haiku $0.008 + orchestrator Sonnet $0.130) | 36 min session (incl. approvals) |

Notes:

- 2026-10-26 08:58, last-24h subagent share: evaluator 47%, generator 22%. With a small fix (3 changes),
  the Evaluator (own `./mvnw verify` + full checklist) cost about twice the Generator. Evaluator cost is
  roughly fixed per iteration; Generator cost depends on how much there is to fix.
- 2026-10-26 11:22, last-24h subagent share: evaluator 43%, generator 20%, monitor 2%.
- The session's "Total cost" includes subagent usage: the Monitor run shows its own `claude-haiku-5-5` line,
  and only the Monitor runs on Haiku. "Total duration (API)" does not seem to include subagents (43s here).
  → The $0.74 for Sprint 1 iteration 2 is the full run cost, not a lower bound.
- Orchestrator overhead: in the Monitor run, the orchestrator (Sonnet, mostly cache reads of its own context)
  cost ~16× the Monitor (Haiku). For small agents the orchestrator, not the agent, dominates the cost.
