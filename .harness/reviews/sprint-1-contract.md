# Sprint 1 contract: Bulk status update with per-item outcomes
Spec: .harness/output/spec.md (APPROVED)   Decisions applied: DEC-1 (without `note`), DEC-4, DEC-5, DEC-6, DEC-7, DEC-8, DEC-9, DEC-10, DEC-13

## Scope in / Scope out

In:
- `PATCH /api/activities/bulk-status` with body `{"updates":[{"taskId","status"}]}`; response `{"updatedCount","failedCount","results":[{"taskId","success","status","errorCode","message"}]}` (DEC-5), always 200 for a request that reaches the service, results in request order, items independent and non-atomic (DEC-4).
- Per-item check order: exists (`NotFoundError`, message `"Task not found: <taskId>"`) -> authorized (assignee, owner or `STORE_MANAGER`, else `ForbiddenError`) -> transition (`ConflictError`). Allowed targets: DONE or BLOCKED from TODO / IN_PROGRESS; BLOCKED -> DONE is allowed (DEC-8; its test is AC-2.1 in Sprint 2, but the rule is implemented here). From DONE, or target equal to current status: `ConflictError`, no update.
- One `TaskStatusChangedEvent` per updated task via `EventBus`, statuses as `TaskStatus.name()` (DEC-9). Only `status` and `updatedAt` change (DEC-10).
- Item `status` typed `TaskStatus` in `BulkStatusItem` (needed so Sprint 2 can distinguish AC-2.2 from AC-2.3).
- Failed item `status` = task's unchanged current status, null when the task does not exist.

Out (unchanged, do not fix):
- Whole-request validation (empty list, >50, duplicates, disallowed target statuses, missing fields): Sprint 2. In this sprint the Generator may add the `@Valid` / `@NotNull` annotations only if needed to compile; no AC tests them.
- Audit entry, `GET /api/activities/{id}/audit`, `note` field: Sprint 3.
- Baseline gaps left unchanged: tasks not store-scoped, existing `PATCH /{id}` has no permission check, `Task.withChanges` has no transition rules, assignee cannot be cleared, `programmeId` not validated, no `Clock` (use `Instant.now()`), events synchronous, `AlertService` unchanged.
- Existing `PATCH /api/activities/{id}`, `UpdateTaskRequest` and `Task` are not modified; `Task.withChanges` is reused with nulls for all fields except status.

## Expected changes

- dto (new, records, `com.storeops.activities.dto`): `BulkStatusRequest(List<BulkStatusItem> updates)`, `BulkStatusItem(String taskId, TaskStatus status)`, `BulkStatusResponse(int updatedCount, int failedCount, List<BulkStatusResult> results)`, `BulkStatusResult(String taskId, boolean success, String status, String errorCode, String message)`.
- controller: `ActivityController` gets one handler `PATCH /bulk-status` that resolves the caller, takes the request body and calls one service method (R4). The literal path takes precedence over `/{id}`.
- service: `ActivityService` gets `bulkUpdateStatus(AuthenticatedUser caller, BulkStatusRequest request)` returning `BulkStatusResponse`; it holds all rules, catches per-item `AppError` subclasses (`NotFoundError`, `ForbiddenError`, `ConflictError`) and maps them to a failed `BulkStatusResult` using the error's code and message.
- repository: existing `TaskRepository` only (`findById`, `save`). No new repository.
- tests: extend `ActivityServiceTest` and `ActivityControllerTest`.

## Acceptance criteria

Setup used by all ACs: tasks are created via `POST /api/activities` or the service in test setup. "T-x" is a placeholder for the task id returned at creation (task ids are generated UUIDs); "T-404" is any id that does not exist. The task owner is the creator.

AC-1.1 - Valid mixed update succeeds for all items
GIVEN T-1 (owner u-manager, assignee u-associate, status TODO) and T-2 (owner u-manager, assignee u-associate, status IN_PROGRESS)
WHEN u-associate sends PATCH /api/activities/bulk-status with header X-User-Id: u-associate and body `{"updates":[{"taskId":"T-1","status":"DONE"},{"taskId":"T-2","status":"BLOCKED"}]}`
THEN the response is 200 with `updatedCount` 2, `failedCount` 0 and `results` in request order: `[{taskId T-1, success true, status "DONE", errorCode null, message null},{taskId T-2, success true, status "BLOCKED", errorCode null, message null}]`
AND GET /api/activities/T-1 returns status DONE and GET /api/activities/T-2 returns status BLOCKED, with assignee, priority and category unchanged
AND exactly two `TaskStatusChangedEvent` are emitted: (T-1, oldStatus "TODO", newStatus "DONE") and (T-2, oldStatus "IN_PROGRESS", newStatus "BLOCKED")
Test: Mockito unit (ActivityServiceTest) for state and events; MockMvc (ActivityControllerTest) for the HTTP body

AC-1.2 - A missing task fails only its own item
GIVEN T-1 exists (owner u-manager, status TODO) and no task "T-404" exists
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-404","status":"DONE"},{"taskId":"T-1","status":"DONE"}]}`
THEN the response is 200 with `updatedCount` 1, `failedCount` 1
AND results[0] is `{taskId "T-404", success false, status null, errorCode "NOT_FOUND", message "Task not found: T-404"}`
AND results[1] is `{taskId "T-1", success true, status "DONE"}`
AND T-1 is DONE afterwards
AND exactly one `TaskStatusChangedEvent` is emitted (for T-1)
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

AC-1.3 - A caller who is neither assignee, owner nor store manager is refused for that item
GIVEN T-1 (owner u-lead, assignee u-lead, status TODO)
WHEN u-associate sends PATCH /api/activities/bulk-status with header X-User-Id: u-associate and body `{"updates":[{"taskId":"T-1","status":"DONE"}]}`
THEN the response is 200 with `updatedCount` 0, `failedCount` 1
AND results[0] is `{taskId "T-1", success false, status "TODO", errorCode "FORBIDDEN"}` with a non-empty `message`
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
Test: Mockito unit (ActivityServiceTest) for the rule; MockMvc (ActivityControllerTest) for the HTTP body

AC-1.4 - A store manager may update a task they neither own nor are assigned to
GIVEN T-3 (owner u-lead, assignee u-lead, status TODO)
WHEN u-manager (STORE_MANAGER) sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-3","status":"BLOCKED"}]}`
THEN the response is 200 with `updatedCount` 1, `failedCount` 0 and results[0] `{taskId "T-3", success true, status "BLOCKED"}`
AND T-3 is BLOCKED afterwards
AND exactly one `TaskStatusChangedEvent` is emitted (T-3, oldStatus "TODO", newStatus "BLOCKED")
Test: Mockito unit (ActivityServiceTest)

AC-1.5 - A DONE task cannot be changed
GIVEN T-1 (owner u-manager, status DONE)
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-1","status":"BLOCKED"}]}`
THEN the response is 200 with `updatedCount` 0, `failedCount` 1
AND results[0] is `{taskId "T-1", success false, status "DONE", errorCode "CONFLICT"}` with a non-empty `message`
AND T-1 remains DONE
AND no `TaskStatusChangedEvent` is emitted
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

AC-1.6 - Setting the current status is a conflict, not an update
GIVEN T-2 (owner u-manager, status BLOCKED)
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-2","status":"BLOCKED"}]}`
THEN the response is 200 with `updatedCount` 0, `failedCount` 1
AND results[0] is `{taskId "T-2", success false, status "BLOCKED", errorCode "CONFLICT"}` with a non-empty `message`
AND T-2's `updatedAt` is unchanged
AND no `TaskStatusChangedEvent` is emitted
Test: Mockito unit (ActivityServiceTest)

## Architecture rules

- R1: no new cross-module access; activities uses only its own `TaskRepository`. Hard gate A1, A3.
- R2: the only side effect is `eventBus.emit(TaskStatusChangedEvent)`; no import of `AlertService` or `ReportService`. Hard gate A5 (partial); Evaluator reviews the rest.
- R3: only `AppError` subclasses are constructed in controller and service (per-item errors are caught and converted to results, never rethrown as raw exceptions). Hard gate A4; Evaluator scans the diff for dead-code throws.
- R4: controller contains no business rules and does not touch repositories. Hard gate A2; logic-in-controller is an Evaluator review.
- R5: not affected. The write method name starts with `update` (A7 convention).

## Test expectations

- AC-1.1: `ActivityServiceTest` asserts both tasks' stored status/assignee/priority/category and captures exactly two `TaskStatusChangedEvent` with the exact String statuses; `ActivityControllerTest` asserts `$.updatedCount`, `$.failedCount`, `$.results[i].taskId/success/status/errorCode/message` (nulls included).
- AC-1.2: service test asserts T-1 saved as DONE and exactly one event; controller test asserts results[0] fields including exact message `"Task not found: T-404"` and `status` null.
- AC-1.3: service test asserts task still TODO (no save of changed state) and `verifyNoInteractions` / no event; controller test asserts `$.results[0].errorCode` "FORBIDDEN", `status` "TODO", non-empty `message`.
- AC-1.4: service test asserts T-3 BLOCKED and one event with oldStatus "TODO", newStatus "BLOCKED".
- AC-1.5: service test asserts T-1 still DONE and no event; controller test asserts counts and `errorCode` "CONFLICT".
- AC-1.6: service test asserts `updatedAt` equals the value before the call, no save of a changed task, no event.
- Tests assert the response and the state change, not only the status code. JaCoCo: service >= 80%, controller >= 70% must hold at the end of this sprint.

## Definition of done

- All ACs AC-1.1 to AC-1.6 pass.
- No unrelated behaviour changed (existing `PATCH /{id}`, `Task`, `UpdateTaskRequest`, `AlertService` untouched; existing tests still pass unmodified).
- `./mvnw verify` exits 0.
- No gate files changed (`ArchitectureTest.java`, JaCoCo thresholds or plugin config in `pom.xml`, Checkstyle/SpotBugs config; no skipped phases; no deleted, disabled or weakened tests). See architecture-principles "Gate integrity".
