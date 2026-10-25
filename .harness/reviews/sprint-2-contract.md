# Sprint 2 contract: Whole-request validation and limits

> **AMENDMENT 2026-10-24 (after Sprint 1 review)**
>
> - The service method is `updateStatusBulk`, not `bulkUpdateStatus` (the original name broke the D7
>   write-prefix rule; see journal "Generator sprint 1, iteration 1").
> - Sprint 1 placeholder behaviour is replaced in this sprint: TODO/IN_PROGRESS targets move from per-item
>   CONFLICT to whole-request VALIDATION_ERROR, and a null/empty `updates` list moves from 200 to 400.
> - Allowed changes to existing tests: `ActivityServiceTest#bulkUpdateWithNullItemsListIsEmptyResponse` may be
>   rewritten to expect `ValidationError`. No other existing test may be changed.

Spec: .harness/output/spec.md (APPROVED) Decisions applied: DEC-2, DEC-3 (except `note`), DEC-8 (BLOCKED -> DONE)

## Scope in / Scope out

In:

- On `PATCH /api/activities/bulk-status`, reject the whole request with `ValidationError` (400, code `VALIDATION_ERROR`) before any task is read or changed, when: `updates` is missing/null or empty; `updates` has more than 50 items; any `taskId` appears twice; any item has a `status` of TODO or IN_PROGRESS (service check); any item has a missing `status` or a blank/missing `taskId` (bean validation); `status` text is not a `TaskStatus` name (Jackson binding failure, mapped by the existing `GlobalExceptionHandler` to message exactly `"Malformed request"`).
- Exactly 50 distinct items is accepted (upper boundary).
- Coverage of the BLOCKED -> DONE transition (implemented in Sprint 1).
- No task update, no `TaskStatusChangedEvent` for a rejected request.

Out (unchanged, do not fix):

- `note` field and its 500-character limit, audit entries and endpoint: Sprint 3.
- `GlobalExceptionHandler` is not modified; its existing mappings are relied upon.
- Baseline gaps unchanged as in Sprint 1 (not store-scoped, existing PATCH unchanged, no `Clock`, events synchronous, etc.).
- `BulkStatusItem.status` stays typed `TaskStatus`; do not change it to String.

## Expected changes

- dto: `BulkStatusRequest` and `BulkStatusItem` (created in Sprint 1) gain Jakarta validation annotations: `@NotEmpty` and `@Valid` on `updates`, `@NotBlank` on `taskId`, `@NotNull` on `status`. Whether the 1-50 size and duplicate checks live in annotations or in the service is the Generator's choice, but the service method must itself reject empty, >50, duplicate and disallowed-target input with `ValidationError` so it holds for service-level calls (AC-2.2, AC-2.5, AC-2.6 are tested at service level).
- controller: `ActivityController` bulk handler adds `@Valid` on the request body; no other logic (R4).
- service: `ActivityService.bulkUpdateStatus` validates the whole request first (non-empty, <= 50, no duplicate `taskId`, every target status is DONE or BLOCKED) and throws `ValidationError` with a non-empty message before processing any item.
- tests: extend `ActivityServiceTest` and `ActivityControllerTest`.

## Acceptance criteria

Setup used by all ACs: tasks are created via `POST /api/activities` or the service in test setup. "T-x" is a placeholder for the task id returned at creation (task ids are generated UUIDs); "T-404" is any id that does not exist. The task owner is the creator.

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

## Architecture rules

- R1: unchanged; no cross-module access. Hard gate A1, A3.
- R2: no new side effects; events only on successful items. Hard gate A5 (partial).
- R3: all rejections are `ValidationError` constructed in the service; the "Malformed request" and bean-validation cases go through the existing `GlobalExceptionHandler`. No raw exceptions in controller or service. Hard gate A4.
- R4: the controller only adds `@Valid`; checks that need request content logic (size, duplicates, allowed targets) are in the service. Hard gate A2; logic-in-controller is an Evaluator review.
- R5: not affected.

## Test expectations

- AC-2.1: `ActivityServiceTest` asserts T-4 saved as DONE and one event with oldStatus "BLOCKED", newStatus "DONE".
- AC-2.2: service test asserts `ValidationError` thrown (for both IN_PROGRESS and TODO), task unchanged, no event; controller test asserts 400, `$.code` "VALIDATION_ERROR", non-empty `$.message`, and T-1 still TODO via GET.
- AC-2.3: controller test asserts 400, `$.code` "VALIDATION_ERROR", `$.message` equal to "Malformed request", T-1 still TODO.
- AC-2.4: controller test covers both cases, asserting 400, code, non-empty message, T-1 still TODO.
- AC-2.5: service test covers empty, 51 items and exactly 50 (updatedCount 50, 50 events); controller test covers `{"updates":[]}`, `{}`, 51 items (400) and 50 items (200, `$.updatedCount` 50). Tasks beyond T-1 are created in test setup.
- AC-2.6: service test asserts `ValidationError`, T-1 still TODO, no event; controller test asserts 400 and body code.
- No test may assert only the status code. JaCoCo: service >= 80%, controller >= 70% must still hold.

## Definition of done

- All ACs AC-2.1 to AC-2.6 pass, and Sprint 1 ACs AC-1.1 to AC-1.6 still pass.
- No unrelated behaviour changed (`GlobalExceptionHandler`, existing `PATCH /{id}`, `Task`, `UpdateTaskRequest` untouched).
- `./mvnw verify` exits 0.
- No gate files changed (`ArchitectureTest.java`, JaCoCo thresholds or plugin config in `pom.xml`, Checkstyle/SpotBugs config; no skipped phases; no deleted, disabled or weakened tests). See architecture-principles "Gate integrity".
