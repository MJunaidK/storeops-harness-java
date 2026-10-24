STATUS: AWAITING APPROVAL
# Spec: Shift handover bulk update

## Feature prompt

Add shift handover bulk update — PATCH /api/activities/bulk-status allowing outgoing shift staff to mark multiple operational activities as DONE or BLOCKED in a single request, with partial failure handling and an audit entry per updated task.

## Outcome

An outgoing shift member sends one request listing tasks and a target status (DONE or BLOCKED) per task. Each task is processed independently: valid ones are updated, invalid ones are reported with an error code, and the caller gets one result per task. Every updated task produces a `TaskStatusChangedEvent` (so alerts react as they do today) and an audit entry recording who changed what, readable through a new endpoint.

## Affected surfaces

- Module: `activities` only. Reads `StaffService` is not needed (no assignee validation). No new cross-module calls.
- New endpoint: `PATCH /api/activities/bulk-status` (Sprint 1). New endpoint: `GET /api/activities/{id}/audit` (Sprint 2).
- Existing classes changed: `ActivityController`, `ActivityService`. Existing `PATCH /api/activities/{id}` is unchanged.
- New classes (activities module, real package layout): DTOs `BulkStatusRequest`, `BulkStatusItem`, `BulkStatusResponse`, `BulkStatusResult`; model `AuditEntry`; `AuditRepository` + `InMemoryAuditRepository` (Sprint 2).
- Events: reuses `TaskStatusChangedEvent` (no new event type). Alerts: `AlertService` unchanged (DONE -> `SHIFT_HANDOVER`, BLOCKED -> `ESCALATION`, existing behaviour).
- Rules: R1 (audit storage is inside activities, no other module's repository), R2 (alerts reached only via `EventBus`), R3 (only `AppError` subclasses), R4 (controller maps HTTP to one service call; all rules in service), R5 not affected.
- Route note: `PATCH /api/activities/bulk-status` is a literal path and takes precedence over `PATCH /api/activities/{id}`.

## Baseline gaps

| Gap | Status |
| --- | --- |
| Tasks not store-scoped | Out of scope: existing behaviour unchanged; gap not fixed by this feature (bulk update can address any task id, same as the existing PATCH). Risk noted below. |
| PATCH has no permission check | In scope for the new endpoint only: AC-1.3. The existing `PATCH /{id}` is not changed. |
| No status transition rules | In scope for the new endpoint only: AC-1.4. `Task.withChanges` and the existing PATCH are not changed. |
| Assignee cannot be cleared | Out of scope: existing behaviour unchanged; gap not fixed by this feature. |
| `programmeId` not validated | Out of scope: existing behaviour unchanged; gap not fixed by this feature. |
| No injectable `Clock` | Out of scope: services keep using `Instant.now()`; tests assert timestamps are non-null, not exact values. |
| Events carry status as String | In scope: events carry `DONE` / `BLOCKED` via `status.name()`; AC-1.1 and AC-1.2 assert the exact strings. |
| Events are synchronous | Out of scope: a listener failure fails the request; not fixed here. |
| Alert types incomplete, no region model, departments, escalation to manager, programme lifecycle | Out of scope: not touched. |

## Decisions

- **DEC-1 Request shape.** `{"updates":[{"taskId":"<id>","status":"DONE|BLOCKED","note":"<optional text>"}]}`. Per-item status (not one status for all) because the prompt says "DONE or BLOCKED" and an outgoing shift usually closes some tasks and blocks others. Rejected: one shared status + id list (less flexible).
- **DEC-2 Batch limits.** 1 to 50 items. Empty list, more than 50 items, a null/blank `updates` or a duplicate `taskId` in the same request is rejected as a whole with `ValidationError` (400, `VALIDATION_ERROR`) and nothing is changed. Duplicates are rejected because two different target statuses for one task make the outcome ambiguous.
- **DEC-3 Item validation (whole request).** Each item needs a non-blank `taskId` and `status` equal to `DONE` or `BLOCKED`; any other value (`TODO`, `IN_PROGRESS`, unknown text, missing) rejects the whole request with 400 `VALIDATION_ERROR`. `note` is optional, max 500 characters; longer rejects the whole request. Rationale: these are malformed requests, not per-task outcomes.
- **DEC-4 Partial failure model.** Valid requests always return 200 with one result per item, in request order, even if every item failed. Items are independent and not atomic; a failed item never rolls back or blocks another. Rejected: 207 Multi-Status (not used elsewhere in the API) and all-or-nothing (contradicts "partial failure handling").
- **DEC-5 Response shape.** `{"updatedCount":n,"failedCount":m,"results":[{"taskId","success","status","errorCode","message"}]}`. For a success item: `success=true`, `status` = new status name, `errorCode=null`, `message=null`. For a failed item: `success=false`, `status` = the task's unchanged current status (null when the task does not exist), `errorCode` = the `AppError` code, `message` = the error message.
- **DEC-6 Authorization (per item).** Any authenticated user may call the endpoint. For each task the caller may update it if the caller is the task's `assigneeId`, or the task's `ownerId`, or has role `STORE_MANAGER`. Otherwise that item fails with `FORBIDDEN` and the task is unchanged. `REGIONAL_MANAGER` and `DEPARTMENT_LEAD` get no special privilege (consistent with `ActivityService.delete`, which privileges only owner and `STORE_MANAGER`). Rationale: "outgoing shift staff" includes associates who hold the task. Rejected: role-only gate (would block associates, the main users).
- **DEC-7 Missing task.** Unknown `taskId` fails that item with `NOT_FOUND`. Authorization is checked after existence (a missing task reports NOT_FOUND).
- **DEC-8 Transitions (per item).** Allowed: `TODO`->`DONE`, `IN_PROGRESS`->`DONE`, `BLOCKED`->`DONE`, `TODO`->`BLOCKED`, `IN_PROGRESS`->`BLOCKED`. Not allowed, item fails with `ConflictError` (`CONFLICT`): from `DONE` to anything (DONE is final for this endpoint), and any request whose target equals the current status (no-op: no update, no event, no audit entry). Check order per item: exists -> authorized -> transition.
- **DEC-9 Events.** For each successfully updated task, exactly one `TaskStatusChangedEvent(taskId, title, ownerId, assigneeId, oldStatus, newStatus, occurredAt)` via `EventBus`, with status values as `TaskStatus.name()`. No event for failed items or for rejected requests. Alert types are not changed.
- **DEC-10 What a bulk update changes.** Only `status` and `updatedAt`. Assignee, priority, category are untouched. The existing PATCH is not modified or reused by the controller; the service gets a new `bulkUpdateStatus` method. (Write-method prefix `update` keeps A7 naming convention.)
- **DEC-11 Audit entry.** `AuditEntry(id, taskId, actorId, oldStatus, newStatus, note, occurredAt)`; status values stored as Strings (enum names), `note` may be null. One entry per successfully updated task, none for failed items or rejected requests. Stored in a new in-memory `AuditRepository` inside the `activities` module (R1). Created in the same service call as the task update. Rationale: keeps audit in the owning module; alerts or other modules do not write it.
- **DEC-12 Audit exposure.** `GET /api/activities/{id}/audit` returns a JSON array of `AuditEntry` for that task, oldest first. Readable by `STORE_MANAGER` and `DEPARTMENT_LEAD`; other roles get `FORBIDDEN`. Unknown task id gives `NOT_FOUND`. Rationale: the audit trail must be verifiable through the API; restricted because it names actors. Audit entries from the existing `PATCH /{id}` are not recorded (out of scope).
- **DEC-13 Auth.** Missing or unknown `X-User-Id` gives 401 `UNAUTHORIZED` through the existing resolver; no new behaviour, no dedicated AC.

## Sprints

### Sprint 1 - Bulk status update with partial failure
- Outcome: `PATCH /api/activities/bulk-status` validates the request, updates allowed tasks, reports failures per item, emits one event per updated task.
- ACs: AC-1.1 to AC-1.6. Decisions: DEC-1 to DEC-10, DEC-13.
- Dependencies: none (uses existing `TaskRepository`, `EventBus`).
- Boundary: the endpoint is demonstrable and shippable without audit; audit adds a new repository, model and endpoint and is a separate verifiable slice.

### Sprint 2 - Audit entry per updated task
- Outcome: each successful bulk update writes an audit entry; entries are readable via `GET /api/activities/{id}/audit`.
- ACs: AC-2.1 to AC-2.5. Decisions: DEC-11, DEC-12.
- Dependencies: Sprint 1.
- Boundary: introduces the only new storage and endpoint; if it fails, Sprint 1 is already green.

## Acceptance criteria

Setup used by all ACs: tasks are created via `POST /api/activities` or the service in test setup. "T-x" denotes a task id returned at creation. Owner is the creator.

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
AND results[0] is `{taskId "T-404", success false, status null, errorCode "NOT_FOUND"}` with a non-empty `message`
AND results[1] is `{taskId "T-1", success true, status "DONE"}`
AND T-1 is DONE afterwards
AND exactly one `TaskStatusChangedEvent` is emitted (for T-1)
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

AC-1.3 - Per-item authorization (assignee, owner or store manager only)
GIVEN T-1 (owner u-lead, assignee u-lead, status TODO), T-2 (owner u-lead, assignee u-associate, status TODO), and T-3 (owner u-lead, assignee u-lead, status TODO)
WHEN u-associate sends PATCH /api/activities/bulk-status with header X-User-Id: u-associate and body `{"updates":[{"taskId":"T-1","status":"DONE"},{"taskId":"T-2","status":"DONE"}]}`
THEN the response is 200 with `updatedCount` 1, `failedCount` 1
AND results[0] is `{taskId "T-1", success false, status "TODO", errorCode "FORBIDDEN"}` and results[1] is `{taskId "T-2", success true, status "DONE"}`
AND T-1 remains TODO and T-2 is DONE
AND exactly one `TaskStatusChangedEvent` is emitted (for T-2)
AND when u-manager (STORE_MANAGER, neither owner nor assignee) sends `{"updates":[{"taskId":"T-3","status":"BLOCKED"}]}`, T-3 becomes BLOCKED and results[0].success is true
Test: Mockito unit (ActivityServiceTest) for the rule; MockMvc (ActivityControllerTest) for the u-associate request

AC-1.4 - Transition rules are enforced per item
GIVEN T-1 (owner u-manager, status DONE), T-2 (owner u-manager, status BLOCKED), T-3 (owner u-manager, status TODO)
WHEN u-manager sends PATCH /api/activities/bulk-status with body `{"updates":[{"taskId":"T-1","status":"BLOCKED"},{"taskId":"T-2","status":"BLOCKED"},{"taskId":"T-3","status":"DONE"}]}`
THEN the response is 200 with `updatedCount` 1, `failedCount` 2
AND results[0] is `{taskId "T-1", success false, status "DONE", errorCode "CONFLICT"}` (DONE is final)
AND results[1] is `{taskId "T-2", success false, status "BLOCKED", errorCode "CONFLICT"}` (target equals current)
AND results[2] is `{taskId "T-3", success true, status "DONE"}`
AND T-1 remains DONE, T-2 remains BLOCKED, and exactly one `TaskStatusChangedEvent` is emitted (for T-3)
AND a task in BLOCKED set to DONE succeeds (verified by a separate case: T-4 BLOCKED -> DONE gives success true)
Test: Mockito unit (ActivityServiceTest)

AC-1.5 - Invalid item fields reject the whole request
GIVEN T-1 (owner u-manager, status TODO) exists
WHEN u-manager sends PATCH /api/activities/bulk-status with a body containing T-1 with status "IN_PROGRESS" (variants: status "TODO", status missing, status "FINISHED", blank taskId, note of 501 characters)
THEN each variant gives 400 with body code "VALIDATION_ERROR" and a non-empty `message`
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
Test: MockMvc (ActivityControllerTest) for each variant; Mockito unit (ActivityServiceTest) for the status and note rules

AC-1.6 - Invalid list rejects the whole request
GIVEN T-1 (owner u-manager, status TODO) exists
WHEN u-manager sends PATCH /api/activities/bulk-status with body `{"updates":[]}` (variants: `{"updates":[{"taskId":"T-1","status":"DONE"},{"taskId":"T-1","status":"BLOCKED"}]}` duplicate id; 51 distinct items; body `{}`)
THEN each variant gives 400 with body code "VALIDATION_ERROR"
AND T-1 remains TODO
AND no `TaskStatusChangedEvent` is emitted
AND a request with exactly 50 distinct valid items returns 200 (boundary)
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

### Sprint 2

AC-2.1 - One audit entry per updated task
GIVEN T-1 (owner u-manager, assignee u-associate, status TODO) and T-2 (same, status IN_PROGRESS)
WHEN u-associate sends PATCH /api/activities/bulk-status with body `{"updates":[{"taskId":"T-1","status":"DONE","note":"Finished on shift"},{"taskId":"T-2","status":"BLOCKED"}]}`
THEN GET /api/activities/T-1/audit by u-manager returns 200 with exactly one entry: taskId "T-1", actorId "u-associate", oldStatus "TODO", newStatus "DONE", note "Finished on shift", non-null `id` and `occurredAt`
AND GET /api/activities/T-2/audit by u-manager returns exactly one entry: oldStatus "IN_PROGRESS", newStatus "BLOCKED", note null
Test: Mockito unit (ActivityServiceTest) for entry creation; MockMvc (ActivityControllerTest) for the GET body

AC-2.2 - No audit entry for failed items or rejected requests
GIVEN T-1 (owner u-lead, assignee u-lead, status TODO), T-2 (owner u-manager, status DONE)
WHEN u-associate sends a bulk request for T-1 to DONE (FORBIDDEN item), and u-manager sends a bulk request for T-2 to BLOCKED (CONFLICT item), and u-manager sends `{"updates":[]}` (400)
THEN GET /api/activities/T-1/audit and GET /api/activities/T-2/audit by u-manager each return 200 with an empty array
Test: Mockito unit (ActivityServiceTest)

AC-2.3 - Audit entries are listed oldest first across repeated updates
GIVEN T-1 (owner u-manager, status TODO)
WHEN u-manager sends bulk updates T-1 -> BLOCKED, then T-1 -> DONE
THEN GET /api/activities/T-1/audit by u-lead returns 200 with two entries in order: (TODO -> BLOCKED), then (BLOCKED -> DONE), both with actorId "u-manager"
Test: Mockito unit (ActivityServiceTest); MockMvc (ActivityControllerTest)

AC-2.4 - Audit trail is restricted to managers and leads
GIVEN T-1 (owner u-manager) with one audit entry
WHEN u-associate sends GET /api/activities/T-1/audit with header X-User-Id: u-associate (and separately u-regional)
THEN each response is 403 with body code "FORBIDDEN"
AND the audit entry for T-1 is unchanged (u-manager still reads one entry)
Test: MockMvc (ActivityControllerTest); Mockito unit (ActivityServiceTest)

AC-2.5 - Audit of a missing task is NOT_FOUND
GIVEN no task "T-404" exists
WHEN u-manager sends GET /api/activities/T-404/audit with header X-User-Id: u-manager
THEN the response is 404 with body code "NOT_FOUND"
Test: MockMvc (ActivityControllerTest)

## Assumptions and risks

- Assumed: JSON field names are as in DEC-1 and DEC-5; changing them requires a spec change.
- Risk: tasks are not store-scoped, so a STORE_MANAGER of one store could bulk-update another store's tasks. Not fixed here (out of scope); consistent with the existing PATCH. Seed data has only store-1, so isolation cannot be demonstrated.
- Risk: events are synchronous; a failing alert listener would fail the whole bulk request after earlier items were already saved (non-atomic). Accepted for now.
- Risk: `UpdateTaskRequest` JSON binding of an invalid enum value (e.g. "FINISHED") surfaces as `HttpMessageNotReadableException`; the existing handler maps it to 400 `VALIDATION_ERROR` with message "Malformed request". AC-1.5 accepts this message.
- Audit data is in-memory and lost on restart, like all storage.
- Skill-vs-source check: no contradiction found between `app-context` and the activities source read.
- `Task`, `UpdateTaskRequest`, and the existing PATCH are not modified; `Task.withChanges` is reused (it already allows `status` only change by passing nulls).
