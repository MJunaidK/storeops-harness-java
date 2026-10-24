STATUS: APPROVED
# Spec: Shift handover bulk update

## Feature prompt

Add shift handover bulk update — PATCH /api/activities/bulk-status allowing outgoing shift staff to mark multiple operational activities as DONE or BLOCKED in a single request, with partial failure handling and an audit entry per updated task.

## Outcome

An outgoing shift member sends one request listing tasks and a target status (DONE or BLOCKED) per task. Each task is processed independently: valid ones are updated, invalid ones are reported with an error code, and the caller gets one result per task. Every updated task produces a `TaskStatusChangedEvent` (so alerts react as they do today) and an audit entry recording who changed what, readable by a STORE_MANAGER through a new endpoint.

## Affected surfaces

- Module: `activities` only. `StaffService` is not needed (no assignee validation). No new cross-module calls.
- New endpoint: `PATCH /api/activities/bulk-status` (Sprints 1-2; optional `note` field added in Sprint 3). New endpoint: `GET /api/activities/{id}/audit` (Sprint 3).
- Existing classes changed: `ActivityController`, `ActivityService`. Existing `PATCH /api/activities/{id}` is unchanged.
- New classes (activities module): DTOs `BulkStatusRequest`, `BulkStatusItem`, `BulkStatusResponse`, `BulkStatusResult`; model `AuditEntry`; `AuditRepository` + `InMemoryAuditRepository` (Sprint 3).
- Events: reuses `TaskStatusChangedEvent` (no new event type). Alerts: `AlertService` unchanged (existing behaviour).
- Rules: R1 (audit storage is inside activities), R2 (alerts reached only via `EventBus`), R3 (only `AppError` subclasses), R4 (controller maps HTTP to one service call; all rules in service), R5 not affected.
- Route note: `PATCH /api/activities/bulk-status` is a literal path and takes precedence over `PATCH /api/activities/{id}`.

## Baseline gaps

| Gap | Status |
| --- | --- |
| Tasks not store-scoped | Out of scope: existing behaviour unchanged; gap not fixed by this feature (bulk update can address any task id, same as the existing PATCH). Risk noted below. |
| PATCH has no permission check | In scope for the new endpoint only: AC-1.3, AC-1.4. The existing `PATCH /{id}` is not changed. |
| No status transition rules | In scope for the new endpoint only: AC-1.5, AC-1.6, AC-2.1. `Task.withChanges` and the existing PATCH are not changed. |
| Assignee cannot be cleared | Out of scope: existing behaviour unchanged; gap not fixed by this feature. |
| `programmeId` not validated | Out of scope: existing behaviour unchanged; gap not fixed by this feature. |
| No injectable `Clock` | Out of scope: services keep using `Instant.now()`; tests assert timestamps are non-null, not exact values. |
| Events carry status as String | In scope: events carry `DONE` / `BLOCKED` via `status.name()`; AC-1.1 asserts the exact strings. |
| Events are synchronous | Out of scope: a listener failure fails the request; not fixed here. |
| Alert types incomplete, no region model, departments, escalation to manager, programme lifecycle | Out of scope: not touched. |

## Decisions

- **DEC-1 Request shape.** `{"updates":[{"taskId":"<id>","status":"DONE|BLOCKED","note":"<optional text>"}]}`. Per-item status because an outgoing shift usually closes some tasks and blocks others. Rejected: one shared status + id list (less flexible). The `note` field is introduced in Sprint 3 (it is only consumed by the audit entry); before Sprint 3 an unknown `note` property is ignored by the default Jackson configuration.
- **DEC-2 Batch limits.** 1 to 50 items. An empty list, a missing/null `updates`, more than 50 items, or a duplicate `taskId` in the same request is rejected as a whole with `ValidationError` (400, code `VALIDATION_ERROR`) and nothing is changed. Duplicates are rejected because two target statuses for one task make the outcome ambiguous.
- **DEC-3 Item validation (whole request).** Each item needs a non-blank `taskId` and a `status` of `DONE` or `BLOCKED`. Rejections are whole-request `ValidationError` (400, `VALIDATION_ERROR`) and differ only in message:
  - `status` `TODO` or `IN_PROGRESS` (valid enum value, not an allowed target): thrown by the service; message is non-empty.
  - `status` text that is not a `TaskStatus` name (e.g. `FINISHED`): Jackson cannot bind the enum, `GlobalExceptionHandler` maps `HttpMessageNotReadableException` to message exactly `"Malformed request"`.
  - missing `status` or blank/missing `taskId`: message is non-empty (bean validation messages have the form `<field> <constraint message>`; exact text not asserted).
  - `note` longer than 500 characters (Sprint 3): message non-empty.
  Rationale: these are malformed requests, not per-task outcomes.
- **DEC-4 Partial failure model.** A valid request always returns 200 with one result per item, in request order, even if every item failed. Items are independent and not atomic; a failed item never rolls back or blocks another. Rejected: 207 Multi-Status (not used elsewhere in the API) and all-or-nothing (contradicts "partial failure handling").
- **DEC-5 Response shape.** `{"updatedCount":n,"failedCount":m,"results":[{"taskId","success","status","errorCode","message"}]}`. Success item: `success=true`, `status` = new status name, `errorCode=null`, `message=null`. Failed item: `success=false`, `status` = the task's unchanged current status (null when the task does not exist), `errorCode` = the `AppError` code (`NOT_FOUND`, `FORBIDDEN`, `CONFLICT`), `message` = the error message.
- **DEC-6 Authorization (per item).** Any authenticated user may call the endpoint. For each task the caller may update it if the caller is the task's `assigneeId`, or the task's `ownerId`, or has role `STORE_MANAGER`. Otherwise that item fails with `ForbiddenError` (`FORBIDDEN`) and the task is unchanged. `REGIONAL_MANAGER` and `DEPARTMENT_LEAD` get no special privilege (consistent with `ActivityService.delete`, which privileges only owner and `STORE_MANAGER`). Rejected: role-only gate (would block associates, the main users).
- **DEC-7 Missing task.** An unknown `taskId` fails that item with `NotFoundError` (`NOT_FOUND`) and message `"Task not found: <taskId>"` (the message of the existing `ActivityService.get`). Authorization is checked after existence.
- **DEC-8 Transitions (per item).** Allowed: `TODO`->`DONE`, `IN_PROGRESS`->`DONE`, `BLOCKED`->`DONE`, `TODO`->`BLOCKED`, `IN_PROGRESS`->`BLOCKED`. Not allowed, item fails with `ConflictError` (`CONFLICT`): from `DONE` to anything (DONE is final for this endpoint), and any request whose target equals the current status (no-op: no update, no event, no audit entry). Check order per item: exists -> authorized -> transition.
- **DEC-9 Events.** For each successfully updated task, exactly one `TaskStatusChangedEvent(taskId, title, ownerId, assigneeId, oldStatus, newStatus, occurredAt)` via `EventBus`, status values as `TaskStatus.name()`. No event for failed items or rejected requests. Alert types are not changed.
- **DEC-10 What a bulk update changes.** Only `status` and `updatedAt`. Assignee, priority, category are untouched. The existing PATCH is not modified or reused by the controller; the service gets a new method `bulkUpdateStatus` (prefix `update` keeps the A7 write-prefix convention).
- **DEC-11 Audit entry.** `AuditEntry(id, taskId, actorId, oldStatus, newStatus, note, occurredAt)`; statuses stored as enum-name Strings, `note` may be null. One entry per successfully updated task; none for failed items or rejected requests. Stored in a new in-memory `AuditRepository` inside `activities` (R1), written in the same service call as the task update. Audit entries from the existing `PATCH /{id}` are not recorded (out of scope).
- **DEC-12 Audit exposure.** `GET /api/activities/{id}/audit` returns a JSON array of `AuditEntry` for that task, oldest first. Readable by `STORE_MANAGER` only; every other role (`DEPARTMENT_LEAD`, `ASSOCIATE`, `REGIONAL_MANAGER`) gets `ForbiddenError` (403, `FORBIDDEN`). Check order: role first, then task existence, so a non-manager gets `FORBIDDEN` even for an unknown id, and a manager gets `NOT_FOUND` (404) with message `"Task not found: <id>"` for an unknown id. A task with no entries returns 200 and `[]`. Rationale: the audit trail names actors, so access is limited to the store manager.
- **DEC-13 Auth.** Missing or unknown `X-User-Id` gives 401 `UNAUTHORIZED` through the existing resolver; no new behaviour, no dedicated AC.

## Sprints

### Sprint 1 - Bulk status update with per-item outcomes
- Outcome: `PATCH /api/activities/bulk-status` updates tasks to DONE/BLOCKED, reports NOT_FOUND / FORBIDDEN / CONFLICT per item, emits one event per updated task.
- ACs: AC-1.1 to AC-1.6. Decisions: DEC-1 (without `note`), DEC-4 to DEC-10, DEC-13.
- Dependencies: none (uses existing `TaskRepository`, `EventBus`).
- Boundary reason: this is the smallest demonstrable endpoint (request in, per-item results and events out) and holds every per-task rule (existence, permission, transitions). It stops at 6 ACs; request-level validation is a different failure class (whole request rejected) and goes to Sprint 2.

### Sprint 2 - Whole-request validation and limits
- Outcome: malformed or out-of-limit requests are rejected with 400 before any task is touched; the remaining valid transition (BLOCKED -> DONE) is covered.
- ACs: AC-2.1 to AC-2.6. Decisions: DEC-2, DEC-3 (except `note`), DEC-8.
- Dependencies: Sprint 1 (endpoint and service method exist).
- Boundary reason: validation changes no storage and no new endpoint, so it can be verified alone on the Sprint 1 endpoint; keeping it out of Sprint 1 keeps that sprint at 6 ACs.

### Sprint 3 - Audit entry per updated task
- Outcome: each successful bulk update writes an audit entry (with the optional `note`); a STORE_MANAGER reads entries via `GET /api/activities/{id}/audit`.
- ACs: AC-3.1 to AC-3.6. Decisions: DEC-1 (`note`), DEC-3 (`note` length), DEC-11, DEC-12.
- Dependencies: Sprint 1 (and Sprint 2 validation for the shared request class).
- Boundary reason: introduces the only new storage, model and endpoint; if it fails, Sprints 1-2 are already green. The `note` field is added here because only the audit entry uses it.

## Acceptance criteria

Setup used by all ACs: tasks are created via `POST /api/activities` or the service in test setup. "T-x" is a placeholder for the task id returned at creation (task ids are generated UUIDs); "T-404" is any id that does not exist. The task owner is the creator.

### Sprint 1

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

### Sprint 2

AC-2.1 - A blocked task can be marked DONE
GIVEN T-4 (owner u-manager, status BLOCKED)
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-4","status":"DONE"}]}`
THEN the response is 200 with `updatedCount` 1, `failedCount` 0 and results[0] `{taskId "T-4", success true, status "DONE"}`
AND T-4 is DONE afterwards
AND exactly one `TaskStatusChangedEvent` is emitted (T-4, oldStatus "BLOCKED", newStatus "DONE")
Test: Mockito unit (ActivityServiceTest)

AC-2.2 - A target status other than DONE or BLOCKED rejects the whole request
GIVEN T-1 (owner u-manager, status TODO) exists
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and a body whose `updates` holds `{"taskId":"T-1","status":"IN_PROGRESS"}` (second case: status "TODO")
THEN each case gives 400 with body code "VALIDATION_ERROR" and a non-empty `message`
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

AC-2.3 - A status text that is not a TaskStatus name is a malformed request
GIVEN T-1 (owner u-manager, status TODO) exists
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-1","status":"FINISHED"}]}`
THEN the response is 400 with body code "VALIDATION_ERROR" and message exactly "Malformed request"
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
Test: MockMvc (ActivityControllerTest)

AC-2.4 - Missing required item fields reject the whole request
GIVEN T-1 (owner u-manager, status TODO) exists
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-1"}]}` (second case: `{"updates":[{"taskId":"","status":"DONE"}]}`)
THEN each case gives 400 with body code "VALIDATION_ERROR" and a non-empty `message`
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
Test: MockMvc (ActivityControllerTest)

AC-2.5 - The number of items must be between 1 and 50
GIVEN T-1 (owner u-manager, status TODO) exists
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[]}` (further cases: body `{}`; 51 distinct items including T-1 with status DONE)
THEN each case gives 400 with body code "VALIDATION_ERROR" and a non-empty `message`
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
AND a request with exactly 50 distinct existing tasks (all TODO, owner u-manager) each set to DONE returns 200 with `updatedCount` 50 (upper boundary)
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

AC-2.6 - A duplicate taskId rejects the whole request
GIVEN T-1 (owner u-manager, status TODO) exists
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-1","status":"DONE"},{"taskId":"T-1","status":"BLOCKED"}]}`
THEN the response is 400 with body code "VALIDATION_ERROR" and a non-empty `message`
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

### Sprint 3

AC-3.1 - One audit entry per updated task, readable by the store manager
GIVEN T-1 (owner u-manager, assignee u-associate, status TODO) and T-2 (same owner and assignee, status IN_PROGRESS)
WHEN u-associate sends PATCH /api/activities/bulk-status with body `{"updates":[{"taskId":"T-1","status":"DONE","note":"Finished on shift"},{"taskId":"T-2","status":"BLOCKED"}]}`
THEN GET /api/activities/T-1/audit with header X-User-Id: u-manager returns 200 with exactly one entry: taskId "T-1", actorId "u-associate", oldStatus "TODO", newStatus "DONE", note "Finished on shift", non-null `id` and `occurredAt`
AND GET /api/activities/T-2/audit by u-manager returns 200 with exactly one entry: oldStatus "IN_PROGRESS", newStatus "BLOCKED", note null
Test: Mockito unit (ActivityServiceTest) for entry creation; MockMvc (ActivityControllerTest) for the GET body

AC-3.2 - No audit entry for failed items or rejected requests
GIVEN T-1 (owner u-lead, assignee u-lead, status TODO), T-2 (owner u-manager, status DONE)
WHEN u-associate sends a bulk request for T-1 to DONE (FORBIDDEN item), then u-manager sends a bulk request for T-2 to BLOCKED (CONFLICT item), then u-manager sends `{"updates":[]}` (400)
THEN GET /api/activities/T-1/audit and GET /api/activities/T-2/audit by u-manager each return 200 with an empty array `[]`
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

AC-3.3 - Audit entries are listed oldest first
GIVEN T-1 (owner u-manager, status TODO)
WHEN u-manager sends bulk updates T-1 -> BLOCKED, then T-1 -> DONE
THEN GET /api/activities/T-1/audit by u-manager returns 200 with two entries in order: (oldStatus "TODO", newStatus "BLOCKED"), then (oldStatus "BLOCKED", newStatus "DONE"), both with actorId "u-manager"
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

AC-3.4 - Every role except STORE_MANAGER is refused the audit trail
GIVEN T-1 (owner u-manager) with one audit entry
WHEN each of u-lead (DEPARTMENT_LEAD), u-associate (ASSOCIATE) and u-regional (REGIONAL_MANAGER) sends GET /api/activities/T-1/audit with its own X-User-Id header
THEN each response is 403 with body code "FORBIDDEN" and a non-empty `message`
AND the response body contains no audit entry data
AND GET /api/activities/T-1/audit by u-manager still returns 200 with exactly one entry
Test: MockMvc (ActivityControllerTest); Mockito unit (ActivityServiceTest)

AC-3.5 - Audit of a missing task is NOT_FOUND for the store manager
GIVEN no task "T-404" exists
WHEN u-manager sends GET /api/activities/T-404/audit with header X-User-Id: u-manager
THEN the response is 404 with body code "NOT_FOUND" and message "Task not found: T-404"
Test: MockMvc (ActivityControllerTest)

AC-3.6 - A note longer than 500 characters rejects the whole request
GIVEN T-1 (owner u-manager, status TODO) exists
WHEN u-manager sends PATCH /api/activities/bulk-status with header X-User-Id: u-manager and body `{"updates":[{"taskId":"T-1","status":"DONE","note":"<501 characters>"}]}`
THEN the response is 400 with body code "VALIDATION_ERROR" and a non-empty `message`
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
AND GET /api/activities/T-1/audit by u-manager returns 200 with `[]`
AND the same request with a note of exactly 500 characters returns 200 with `updatedCount` 1 (boundary)
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

## Assumptions and risks

- Error codes and messages verified against `com.storeops.shared.error` and `GlobalExceptionHandler`: codes `NOT_FOUND` (404), `FORBIDDEN` (403), `CONFLICT` (409), `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401) match the subclasses. `GlobalExceptionHandler` maps `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MissingRequestValueException` to `ValidationError("Malformed request")`, and `MethodArgumentNotValidException` to `ValidationError` with message `<field> <constraint message>` joined by "; " (or "Request validation failed"). The only exact messages asserted are "Malformed request" (AC-2.3) and `"Task not found: <id>"` (AC-1.2, AC-3.5, from `ActivityService.get`). All other messages are asserted non-empty. Correction from the previous version: AC-1.5 no longer accepted "Malformed request" for all variants; only the unknown-enum case (now AC-2.3) yields it, the other cases yield service or bean-validation messages.
- Assumed: JSON field names are as in DEC-1 and DEC-5; changing them requires a spec change.
- Risk: binding of an invalid enum value (e.g. `"FINISHED"`) in `BulkStatusRequest` / `BulkStatusItem.status` surfaces as `HttpMessageNotReadableException` before the service runs; `GlobalExceptionHandler` maps it to 400 `VALIDATION_ERROR` with message "Malformed request". This requires `BulkStatusItem.status` to be typed `TaskStatus` (not String) so that AC-2.2 (valid enum, disallowed target) and AC-2.3 (unknown text) stay distinct. `UpdateTaskRequest` is not involved.
- Risk: tasks are not store-scoped, so a STORE_MANAGER of one store could bulk-update another store's tasks or read its audit trail. Not fixed here (out of scope); consistent with the existing PATCH. Seed data has only store-1, so isolation cannot be demonstrated.
- Risk: events are synchronous; a failing alert listener would fail the whole bulk request after earlier items were already saved (non-atomic). Accepted for now.
- Risk: the `note` field is ignored (not rejected) by Sprints 1-2 because Jackson tolerates unknown properties; it only takes effect in Sprint 3.
- Audit data is in-memory and lost on restart, like all storage.
- Skill-vs-source check: no contradiction found between `app-context` and the activities source and error classes read.
- `Task`, `UpdateTaskRequest`, and the existing PATCH are not modified; `Task.withChanges` is reused (a status-only change by passing nulls for the other fields).
