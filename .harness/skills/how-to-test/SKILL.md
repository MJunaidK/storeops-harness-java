---
name: how-to-test
description: How StoreOps tests are written — Mockito unit tests for rules, MockMvc tests for the HTTP contract, and what every test must assert so it proves the business rule, not just the status code. Read by the Generator; used by the Evaluator as reference.
---

# StoreOps — How to Test

## Purpose

Prevent client failure mode #3: tests that check a status code but not the business rule. Every acceptance criterion (AC) in the sprint contract is proven by at least one test that asserts the AC's full THEN clause. Patterns below come from the existing `ActivityServiceTest` and `ActivityControllerTest`.

## Two test levels

| Level                                                                             | Use for                                                   | Setup (copy from existing tests)                                                                                                      |
| --------------------------------------------------------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Mockito unit — `<Service>Test` in `src/test/java/com/storeops/<module>/service/`  | business rules, state changes, events, thrown `AppError`s | `@ExtendWith(MockitoExtension.class)`; `@Mock` for repository, `StaffService`, `EventBus`; `@InjectMocks` for the service             |
| MockMvc — `<Controller>Test` in `src/test/java/com/storeops/<module>/controller/` | HTTP contract: status, JSON body, error body, auth header | `@SpringBootTest` + `@AutoConfigureMockMvc`; `@Autowired MockMvc mvc` — the whole app with real in-memory repositories and seed users |

The sprint contract's `Test:` line says which level(s) each AC needs. Use exactly those classes; extend them, do not create parallel test classes for the same service or controller.

## One AC → named tests

- Each AC has at least one test. Put the AC ID in a comment on the line above the test: `// AC-1.3`.
- Test method names are camelCase and describe the behaviour: `bulkUpdateByUnrelatedAssociateIsForbiddenPerItem`. **No underscores** — Checkstyle `MethodName` fails the build on `ac1_3_forbidden`.
- The generator summary lists each AC with its test class and method.

## What every test asserts

Assert the AC's whole THEN clause, in this order:

1. **Status** — `status().isOk()` / `isForbidden()` / …
2. **Body** — every field the AC names, with exact values: `jsonPath("$.updatedCount").value(1)`, `jsonPath("$.results[0].errorCode").value("FORBIDDEN")`. For errors, always `jsonPath("$.code")`; assert `$.message` exactly when the AC gives the text, otherwise `isNotEmpty()`.
3. **State afterwards** — MockMvc: a follow-up `GET` (e.g. after a refused change, `GET /api/activities/{id}` still shows the old status). Unit: `verify(taskRepository, never()).save(any())`, or capture the saved `Task` and assert its fields.
4. **Events** — emitted: `ArgumentCaptor<TaskStatusChangedEvent>` + `verify(eventBus).emit(captor.capture())`, then assert `taskId`, `oldStatus`, `newStatus` as exact Strings (`"TODO"`, `"DONE"`). Exactly N events: `verify(eventBus, times(N)).emit(any())`. None: `verify(eventBus, never()).emit(any())`.

A test that stops after step 1 does not count as testing an AC.

## Positive and negative cases

- For every rule, test one caller or input that **is allowed** and one that **is refused**.
- A negative test proves nothing happened: state unchanged (step 3) and no event (step 4).
- Boundaries named in the AC are tested on both sides (e.g. 50 items accepted, 51 rejected; 500-character note accepted, 501 rejected).

## Test data

- **MockMvc:** callers are the seed users via header `X-User-Id`: `u-manager` (STORE_MANAGER), `u-lead` (DEPARTMENT_LEAD), `u-associate` (ASSOCIATE), `u-regional` (REGIONAL_MANAGER). Create tasks through the API with the existing `createTask(...)` helper and use the returned id. The application context and repositories are **shared by all tests in the class**: never assume an empty repository or a fixed count of all tasks — use fresh data (e.g. `"p-" + UUID.randomUUID()` as programmeId) and assert only on the ids you created.
- **Unit:** callers are `AuthenticatedUser` records (`new AuthenticatedUser("o1", "s1", StaffRole.ASSOCIATE)`); build tasks with the class's `task(TaskStatus)` helper; stub `findById` with `Optional.of(...)` / `Optional.empty()` and `save` with `thenAnswer(inv -> inv.getArgument(0))`.
- Only store-1 exists in seed data; a store-isolation test must create its own second-store data.

## Never

- Assert only the status code. Existing examples of this weakness — do not copy: `listWithSeededUserReturnsOk`, `patchUnknownTaskIsNotFound`, `deleteUnknownTaskIsNotFound`.
- Assert only `isNotNull()` / `isNotEmpty()` where the AC gives an exact value.
- Modify, delete, `@Disabled` or weaken an existing test to make the build pass; existing tests may change only where the contract explicitly allows it (e.g. constructor wiring).
- Mock the class under test, use `Thread.sleep`, or depend on test execution order.
- Add tests that exist only to raise coverage without asserting behaviour.

## Coverage

JaCoCo (`./mvnw verify`): service packages ≥ 80% line, controller ≥ 70%, shared ≥ 60%, overall ≥ 70%. Coverage is a floor, not evidence: an AC is tested only when its THEN clause is asserted. Never add JaCoCo exclusions.
