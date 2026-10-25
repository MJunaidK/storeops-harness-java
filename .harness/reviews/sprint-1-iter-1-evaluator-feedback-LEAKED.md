VERDICT: FAIL
# Evaluator feedback — Sprint 1, iteration 1
Contract: .harness/output/sprint-1-contract.md · Summary: .harness/output/generator-summary.md · Diff base: main

## Integrity
| Check | Result | Evidence |
|---|---|---|
| GI-1 | PASS | `git diff main --stat` lists 8 files (4 dto, ActivityService, ActivityController, 2 tests); no ArchitectureTest.java, pom.xml or config/ file. |
| GI-2 | PASS | Test diffs only add imports (`nullValue`, `ResultActions`, `times`, Bulk* DTOs) and new methods; no deletions, no @Disabled. |
| GI-3 | PASS | No skip/exclude/@SuppressWarnings in the diff. |

## Hard gates (own `./mvnw verify` run)
| Sub-gate | Result | Evidence (build output line) |
|---|---|---|
| Checkstyle | PASS | `You have 0 Checkstyle violations.` |
| Tests | PASS | `Tests run: 78, Failures: 0, Errors: 0, Skipped: 0` |
| ArchUnit (HG-2) | PASS | `Tests run: 7, Failures: 0, Errors: 0 ... in com.storeops.ArchitectureTest` |
| SpotBugs | PASS | `BugInstance size is 0` |
| JaCoCo | PASS | `All coverage checks have been met.` (BUILD SUCCESS, HG-1 PASS) |

## Checks
| Check | Sev. | Result | Evidence (file:line + quote, or what was verified) |
|---|---|---|---|
| CC-1 | B | PASS | Summary AC table lists a class#method for AC-1.1 to AC-1.6. |
| CC-2 | B | PASS | All 11 named tests exist in ActivityServiceTest / ActivityControllerTest (e.g. `bulkUpdateToCurrentStatusIsConflictAndLeavesUpdatedAtUnchanged`, `bulkStatusOfDoneTaskIsConflictForItem`). |
| CC-3 | C | PASS | Path `/bulk-status`, PATCH, DTO fields and error codes match. Service method `updateStatusBulk` vs contract `bulkUpdateStatus` is listed in Known gaps citing the write-prefix rule (R5 / coding-conventions, D7). |
| TQ-1 | B | PASS | Controller tests assert counts, every result field (nulls included) and follow-up GETs (AC-1.1, 1.2, 1.3, 1.5); service tests assert saved state and captured events (AC-1.1, 1.2, 1.4). |
| TQ-2 | B | PASS | AC-1.3/1.5/1.6 service tests use `verify(taskRepository, never()).save(any())` and `verify(eventBus, never()).emit(any())`; controller tests do follow-up GET. |
| TQ-3 | B | PASS | Exact `"NOT_FOUND"`, `"Task not found: T-404..."`, `"FORBIDDEN"`, `"CONFLICT"`, counts and statuses asserted. |
| TQ-4 | C | FAIL | ActivityServiceTest.java:314 `assertThat(t2.updatedAt()).isEqualTo(before);` where `before = t2.updatedAt()` of an immutable record (true by construction). |
| TQ-5 | M | PASS | Controller tests create their own tasks with UUID programme ids / missing ids; no total counts assumed. |
| AR-1 | B | PASS | ActivityController.java `updateStatusBulk` is a single `return activityService.updateStatusBulk(caller, request);`, no logic. |
| AR-2 | B | PASS | Only `new ForbiddenError`, `new ConflictError` in the diff's src/main; no raw Throwable. |
| AR-3 | B | PASS | Only `taskRepository`, `eventBus` used; no new cross-module call, no AlertService/ReportService import. |
| AR-4 | B | PASS | Not applicable: no new repository; existing `findById`/`save` only. |
| AR-5 | B | PASS | `updateStatusBulk`, `updateOneStatus` start with `update`. |
| AR-6 | B | PASS | Not applicable: reports untouched. |
| SC-1 | B | PASS | No validation, audit or note added; `@Valid` only (allowed). |
| SC-2 | B | FAIL | ActivityServiceTest.java:331 `bulkUpdateWithNullItemsListIsEmptyResponse` asserts zero counts and empty results (a 200-style success) for a null `updates` list, which Sprint 2 must reject (whole-request validation, Scope out). Backed by BulkStatusRequest.java:8 `updates = updates == null ? List.of() : ...`. |
| SC-3 | C | PASS | Every changed file matches Expected changes (4 dto, controller, service, 2 tests). |
| SH-1 | B | PASS | Summary: BUILD SUCCESS, 78 tests, 0 failures; own run identical. |
| SH-2 | C | PASS | Summary Files changed lists exactly the 8 files in the diff with correct layers. |
| SH-3 | B | PASS | Method rename, null-list placeholder and TODO/IN_PROGRESS targets listed under Known gaps. |

## Scores
Architecture 100/100 · Functional 82/100 · Quality 100/100 · Total 93/100

## Verdict reasoning
Step 3: SC-2 (B) failed -> FAIL. Integrity and hard gates passed. TQ-4 (C) also failed.

## Must fix (FAIL) / Conditions (CONDITIONAL PASS) / Notes (PASS)
1. [SC-2] src/test/java/com/storeops/activities/service/ActivityServiceTest.java:331 — remove `bulkUpdateWithNullItemsListIsEmptyResponse`; it asserts behaviour for a null list that Sprint 2 must reject. Also drop the null-to-empty normalisation at src/main/java/com/storeops/activities/dto/BulkStatusRequest.java:8 (keep only `List.copyOf` if a copy is wanted, without defining null behaviour), and update the Known gaps entry.
2. [TQ-4] src/test/java/com/storeops/activities/service/ActivityServiceTest.java:314 — `assertThat(t2.updatedAt()).isEqualTo(before)` compares an immutable record to itself. Remove it, or assert something real (e.g. `verify(taskRepository, never()).save(any())` already proves no change; keep that).
