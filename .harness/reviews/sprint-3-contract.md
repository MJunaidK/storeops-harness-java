# Sprint 3 contract: Audit entry per updated task
Spec: .harness/output/spec.md (APPROVED)   Decisions applied: DEC-1 (`note`), DEC-3 (`note` length), DEC-11, DEC-12

## Scope in / Scope out

In:
- Optional `note` (String, max 500 characters) on `BulkStatusItem`. A longer note rejects the whole request with `ValidationError` (400, `VALIDATION_ERROR`, non-empty message), nothing changed, no audit entry. Exactly 500 characters is accepted.
- `AuditEntry(id, taskId, actorId, oldStatus, newStatus, note, occurredAt)`; statuses as enum-name Strings; `note` may be null; `id` is a generated non-null String; `occurredAt` is a non-null `Instant`.
- One entry per successfully updated task, written in the same `bulkUpdateStatus` call as the task update (after the task is saved). None for failed items or rejected requests. `actorId` is the caller's id.
- New in-memory audit storage inside the activities module (R1): `AuditRepository` interface + `InMemoryAuditRepository`, insertion order preserved (oldest first).
- `GET /api/activities/{id}/audit`: JSON array of `AuditEntry` for that task, oldest first; `[]` when none. Check order: role first (only `STORE_MANAGER` allowed, else `ForbiddenError` 403 `FORBIDDEN`, including for unknown ids), then task existence (`NotFoundError` 404 `NOT_FOUND`, message `"Task not found: <id>"`).

Out (unchanged, do not fix):
- Audit entries from the existing `PATCH /api/activities/{id}` are not recorded.
- Audit is not store-scoped (tasks are not store-scoped); a STORE_MANAGER can read any task's audit.
- No new `DomainEvent`, no `AlertService` change, no pagination or filtering on the audit endpoint.
- Baseline gaps unchanged as in Sprints 1-2.
- Sprint 1 and 2 behaviour (response shape, per-item rules, whole-request validation) is unchanged; the response does not include `note` or audit data.

## Expected changes

- model (new record, `com.storeops.activities.model`): `AuditEntry`.
- repository (new, `com.storeops.activities.repository`): interface `AuditRepository` (save an entry; find entries by task id in insertion order) and `InMemoryAuditRepository` (thread-safe in-memory storage, like `InMemoryTaskRepository`).
- dto: `BulkStatusItem` gains `String note` with `@Size(max = 500)`.
- service: `ActivityService` gets `AuditRepository` injected; `bulkUpdateStatus` saves one `AuditEntry` per updated task; new read method `listAudit(AuthenticatedUser caller, String taskId)` returning `List<AuditEntry>` (name uses the read prefix `list`; role check then existence check inside the service). The 500-character limit must also be enforced in the service (`ValidationError`) so it holds for service-level calls, in addition to or instead of the annotation.
- controller: `ActivityController` gets `GET /{id}/audit` that resolves the caller and calls `ActivityService.listAudit` (R4); it must not access `AuditRepository`.
- tests: extend `ActivityServiceTest` and `ActivityControllerTest`; update their setup/constructor wiring for the new `AuditRepository` dependency without weakening existing tests. Add a test for `InMemoryAuditRepository` only if needed to keep JaCoCo thresholds green.

## Acceptance criteria

Setup used by all ACs: tasks are created via `POST /api/activities` or the service in test setup. "T-x" is a placeholder for the task id returned at creation (task ids are generated UUIDs); "T-404" is any id that does not exist. The task owner is the creator.

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

## Architecture rules

- R1: audit storage lives in `com.storeops.activities.repository`, used only by `ActivityService`; no other module touches it. Hard gate A1, A3.
- R2: no new cross-module call and no new event; `TaskStatusChangedEvent` behaviour from Sprint 1 unchanged. Hard gate A5 (partial).
- R3: `ForbiddenError`, `NotFoundError`, `ValidationError` only; no raw exceptions in controller, service, model, repository or dto (the last three are an Evaluator check). Hard gate A4 (controller and service).
- R4: the audit GET handler maps HTTP to one service call; the role check lives in the service; the repository has no HTTP types and calls no service. Hard gate A2; the rest is an Evaluator review.
- R5: not affected. `listAudit` uses a read prefix; the write path stays in `bulkUpdateStatus` (prefix `update`).

## Test expectations

- AC-3.1: service test captures the saved `AuditEntry` values (taskId, actorId, old/new status Strings, note, non-null id and occurredAt) and asserts exactly one per task; controller test asserts the GET JSON array length 1 and each field, `$[0].note` null for T-2.
- AC-3.2: service test asserts `listAudit` returns an empty list for both tasks after the FORBIDDEN, CONFLICT and rejected-request calls; controller test asserts body `[]` for both GETs.
- AC-3.3: service and controller tests assert order of the two entries by index and both actorId values.
- AC-3.4: controller test iterates the three non-manager users asserting 403, `$.code` "FORBIDDEN", non-empty `$.message`, no `taskId`/`oldStatus` fields in the body, and then the manager GET still returns one entry; service test asserts `ForbiddenError` for the three roles.
- AC-3.5: controller test asserts 404, `$.code` "NOT_FOUND", `$.message` exactly "Task not found: T-404". Also cover (as part of AC-3.4 tests) that a non-manager asking for an unknown id gets 403, per DEC-12 check order.
- AC-3.6: service test asserts `ValidationError` for 501 characters and success for 500; controller test asserts 400 with body code, T-1 still TODO, audit `[]`, then 200 with `$.updatedCount` 1 for the 500-character note.
- No test may assert only the status code. JaCoCo: service >= 80%, controller >= 70%, overall >= 70% must hold, including the new repository and model.

## Definition of done

- All ACs AC-3.1 to AC-3.6 pass, and Sprint 1 and 2 ACs (AC-1.1 to AC-2.6) still pass.
- No unrelated behaviour changed (existing `PATCH /{id}`, `Task`, `UpdateTaskRequest`, `GlobalExceptionHandler`, `AlertService` untouched; existing tests only changed where constructor wiring requires it, never weakened).
- `./mvnw verify` exits 0.
- No gate files changed (`ArchitectureTest.java`, JaCoCo thresholds or plugin config in `pom.xml`, Checkstyle/SpotBugs config; no skipped phases; no deleted, disabled or weakened tests). See architecture-principles "Gate integrity".
