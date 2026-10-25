---
name: grading-criteria
description: How the Evaluator turns gate results and how-to-review check answers into scores and a deterministic verdict (PASS / CONDITIONAL PASS / FAIL) for a StoreOps sprint. Read by the Evaluator.
---

# StoreOps — Grading Criteria

## Purpose

Same inputs → same verdict. The verdict is computed from recorded facts (gate results, PASS/FAIL per check) by fixed rules; the Evaluator never decides how serious a finding "feels". Checks are defined in `how-to-review`; this file only says what their results mean.

## Order of evaluation

Apply the steps in this order and stop at the first FAIL:

1. **Integrity** — any of GI-1, GI-2, GI-3 fails → **FAIL**. No score is computed: gates that were weakened cannot be trusted.
2. **Hard gates** — from the Evaluator's own `./mvnw verify` run, any sub-gate fails → **FAIL**.
3. **Checks** — any check with severity **B** fails → **FAIL**.
4. **Score** — compute dimension scores and the total (below).
5. **Verdict** — any **C** check fails, or total < 80 → **CONDITIONAL PASS**; otherwise → **PASS**.

A check that cannot be answered with evidence counts as **failed**. When in doubt, the result is the stricter one.

## Hard gates (sub-gates of `./mvnw verify`)

Read each from the build output; report each separately.

| Sub-gate   | Passes when                                                                      | Dimension                      |
| ---------- | -------------------------------------------------------------------------------- | ------------------------------ |
| Checkstyle | `You have 0 Checkstyle violations`                                               | Code quality & coverage        |
| Tests      | `Tests run: n, Failures: 0, Errors: 0` for all classes except `ArchitectureTest` | Functional correctness & tests |
| ArchUnit   | `ArchitectureTest`: `Tests run: 7, Failures: 0` (HG-2)                           | Architecture compliance        |
| SpotBugs   | `BugInstance size is 0`                                                          | Code quality & coverage        |
| JaCoCo     | `All coverage checks have been met`                                              | Code quality & coverage        |

If the build stops early (e.g. Checkstyle fails in `validate`), the later sub-gates did not run: report them as "not run" — the verdict is already FAIL.

## Severity per check

**B** = blocking (fail → FAIL) · **C** = conditional (fail → at most CONDITIONAL PASS) · **M** = minor (fail → score only)

| Check                                     | Sev. |     | Check                                   | Sev.               |
| ----------------------------------------- | ---- | --- | --------------------------------------- | ------------------ |
| CC-1 AC listed with test                  | B    |     | AR-1 controller one call, no logic      | B                  |
| CC-2 named test exists                    | B    |     | AR-2 no raw Throwable (incl. dead code) | B                  |
| CC-3 names match contract                 | C    |     | AR-3 no cross-module writes             | B                  |
| TQ-1 full THEN asserted                   | B    |     | AR-4 storage/repository rules           | B                  |
| TQ-2 "unchanged / no event" asserted      | B    |     | AR-5 write-method prefixes              | B                  |
| TQ-3 exact values asserted                | B    |     | AR-6 reports read-only                  | B                  |
| TQ-4 no assertion true by construction    | C    |     | SH-1 verify claim matches               | B                  |
| TQ-5 own test data                        | M    |     | SH-2 files table exact                  | C                  |
| SC-1 nothing from Scope out               | B    |     | SH-3 deviations listed                  | B                  |
| SC-2 no test of later/Scope-out behaviour | B    |     | GI-1 … GI-3 gate integrity              | Integrity (step 1) |
| SC-3 changed files covered                | C    |     |                                         |                    |

## Dimensions and score

| Dimension                      | Weight | Hard gate(s)                 | Checks scored                              |
| ------------------------------ | ------ | ---------------------------- | ------------------------------------------ |
| Architecture compliance        | 40%    | ArchUnit                     | AR-1 … AR-6 (6)                            |
| Functional correctness & tests | 40%    | Tests                        | CC-1 … CC-3, TQ-1 … TQ-5, SC-1 … SC-3 (11) |
| Code quality & coverage        | 20%    | Checkstyle, SpotBugs, JaCoCo | SH-1 … SH-3 (3)                            |

- Dimension score = passed checks ÷ applicable checks × 100 (a "not applicable" check is left out of both). Round to whole numbers.
- Total = 0.4 × Architecture + 0.4 × Functional + 0.2 × Quality.
- Report all three dimension scores and the total, even when the verdict is FAIL from step 3.

## What the Generator must fix

- **FAIL:** every failed sub-gate and every failed B and C check, each with file:line — the Generator's next iteration fixes all of them.
- **CONDITIONAL PASS:** the failed C and M checks, listed as conditions.
- **PASS:** failed M checks, listed as notes.

## Worked example (Sprint 1, iteration 1)

Integrity PASS; all sub-gates PASS; SC-2 FAIL (B: `bulkUpdateWithNullItemsListIsEmptyResponse` asserts 200 for input Sprint 2 must reject); TQ-4 FAIL (C: `ActivityServiceTest` asserts `updatedAt` of an immutable record against itself); CC-3 PASS (name deviation listed with D7 reason). → Step 3: B failed → **FAIL**. Functional 9/11 = 82, Architecture 100, Quality 100 → total 93 (reported, not decisive).
