---
name: architecture-principles
description: The non-negotiable StoreOps architecture rules, why each exists, how each is checked. Read by Planner, Generator and Evaluator.
---

# StoreOps — Architecture Principles

## Purpose

The five architecture rules from the assignment spec (§3.5), encoded for StoreOps. Every rule traces to a client failure mode. Planner: write acceptance criteria that respect them. Generator: never violate them. Evaluator: check each one using the stated method; a hard-gate failure is always a FAIL.

Two numbering systems are used below: **R1–R5** = architecture rules (this file). **A1–A7** = ArchUnit tests in `src/test/java/com/storeops/ArchitectureTest.java`. Always cite an A-rule by its name too; the name is what a failing build prints.

## Hard gate

`./mvnw verify` must exit 0. It runs, in order: Checkstyle → compile → unit, MockMvc and ArchUnit tests (A1–A7) → JaCoCo thresholds (service ≥ 80% line, controller ≥ 70%, shared ≥ 60%, overall ≥ 70%) → SpotBugs (high priority).
`./mvnw test` is NOT sufficient: it skips JaCoCo and SpotBugs. (D6)

## Traceability

| Rule                 | Failure mode | Hard gate (ArchUnit)                                                                                                         | LLM-assessed                                                 |
| -------------------- | ------------ | ---------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| R1 Module boundary   | #1           | A1 `repositoriesAreOnlyAccessedFromTheirOwnModule`, A3 `MODULES_ARE_FREE_OF_CYCLES`                                          | cycles via `shared`                                          |
| R2 Event bus only    | #4           | A5 `ACTIVITIES_AND_PROGRAMMES_DO_NOT_DEPEND_ON_ALERTS_OR_REPORTS` (partial)                                                  | all cross-module writes                                      |
| R3 Error contract    | #2           | A4 `CONTROLLERS_AND_SERVICES_CONSTRUCT_ONLY_APP_ERRORS`                                                                      | dead code; raw exceptions outside controller/service         |
| R4 Layer separation  | —            | A2 `CONTROLLERS_NEVER_ACCESS_REPOSITORIES`                                                                                   | logic in controllers; HTTP or external calls in repositories |
| R5 Read-only reports | —            | A6 `REPORTS_DEPEND_ON_OTHER_MODULES_ONLY_THROUGH_SERVICE_AND_MODEL`, A7 `REPORTS_NEVER_CALL_WRITE_METHODS_ON_OTHER_SERVICES` | write methods without a write prefix                         |
| Test quality         | #3           | JaCoCo thresholds (quantity only)                                                                                            | assertions on business rules (see `how-to-test`)             |

## Rules

### R1 · Module boundary (failure mode #1)

**Rule:** No module accesses another module's `repository` package. Cross-module reads go through the target module's **service** (spec §3.5). Using another module's `model` types returned by its service is allowed. No dependency cycles between modules.

**Why:** A repository is a module's private storage. Reaching into it bypasses that module's rules (validation, store scoping) and couples modules to storage details.

**Correct:**
User member = staffService.requireById(request.userId()); // programmes reads staff via its service
**Violation:**
private final TaskRepository taskRepository; // in AlertService — reaching into activities

**Checked by:** A1 and A3 — HARD GATE. A1 verified by sabotage test B (2026-10-19).
**Blind spot:** A3 ignores `shared`, and `shared.security` depends on `staff`; a cycle routed through `shared` is not detected → Evaluator checks new imports of `shared` from `staff`.

### R2 · Event bus only (failure mode #4)

**Rule:** Reads across modules = direct call to the other module's service, read methods only (`get*`, `find*`, `list*`, `require*`). Side effects across modules (create, change, notify) = `eventBus.emit(...)`; the owning module reacts in its own service with `@EventListener`. Never import or call `AlertService` or `ReportService` from another module. (D9)

**Why:** Direct cross-module writes couple modules: every new reaction to a task would mean editing activities. With events, a new listener is added and the emitting module stays unchanged.

**Correct:**
eventBus.emit(new TaskCreatedEvent(saved.id(), saved.title(), saved.ownerId(), saved.assigneeId(), now));
**Violation:**
alertService.notify(task.assigneeId(), ...); // activities calling alerts directly
activityService.create(caller, request); // programmes writing into activities

**Events:** defined in `com.storeops.shared.event`, implement `DomainEvent`, carry ids and primitive values only, never another module's model types (D5). Status travels as its enum name (String); listeners comparing it must use the enum name, and a test must cover the comparison.

**Checked by:** A5 — HARD GATE, partial: covers only activities/programmes → alerts/reports.
**Blind spot:** A write from any other direction (e.g. programmes → `ActivityService.create`, alerts → activities) is not detected → Evaluator must check every new cross-module service call is a read method.

### R3 · Error contract (failure mode #2)

**Rule:** Controllers and services construct only `AppError` subclasses from `com.storeops.shared.error`. Any other `Throwable` (`RuntimeException`, `IllegalArgumentException`, `UnsupportedOperationException`, a home-made `extends RuntimeException`, …) is forbidden. (D8)

**Why:** Non-`AppError` exceptions bypass `GlobalExceptionHandler`'s mapping, so clients get a 500 instead of the intended status and `{"code","message"}` body.

**Correct:**
return taskRepository.findById(id).orElseThrow(() -> new NotFoundError("Task not found: " + id));
**Violation:**
return taskRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("not found"));

**Mapping:** missing → `NotFoundError` 404 · invalid input or reference → `ValidationError` 400 · no permission → `ForbiddenError` 403 · duplicate → `ConflictError` 409 · no or unknown caller → `UnauthorizedError` 401. A new kind of error = a new `AppError` subclass in `shared.error`, never a raw exception.

**Checked by:** A4 (whitelist) — HARD GATE. Verified by sabotage tests A and F.
**Blind spots:** (1) A4 reads bytecode; dead code such as `if (false) throw …` is removed by javac and not seen (sabotage test C) → Evaluator scans the diff source. (2) A4 covers controller and service packages only → raw exceptions in model, repository or dto classes are an LLM check.

### R4 · Layer separation

**Rule:** Controller → Service → Repository, no skipping. Controllers only map HTTP to one service call (resolve caller, `@Valid` request, return result) and contain no business logic. Repositories only store and fetch: no HTTP types (`ResponseEntity`, `HttpStatus`, `org.springframework.web..`), no calls to services or other modules.

**Why:** Business rules in controllers are skipped when another entry point (event listener, scheduler) calls the service. HTTP concerns in repositories tie storage to the web layer.

**Correct:**
public Task get(AuthenticatedUser caller, @PathVariable String id) { return activityService.get(id); }
**Violation:**
if (caller.role() != StaffRole.STORE_MANAGER) { ... } // permission rule inside a controller

**Checked by:** A2 (controllers never access repositories) — HARD GATE.
**Blind spot:** logic in controllers, HTTP types in repositories, repositories calling services → LLM check. Each changed file in the Generator summary must name its layer (spec §3.5).

### R5 · Read-only reports

**Rule:** The reports module aggregates data from activities, programmes and staff and never writes to them. It may use their `service` and `model` packages only, and call read methods only. Writing its own `ReportRepository` is allowed.

**Why:** Reports must be safe to run at any time; a report that changes data makes numbers unreproducible and can corrupt operational state.

**Correct:**
List<Task> tasks = activityService.list(programmeId, null);
**Violation:**
activityService.delete(caller, taskId); // write from reports

**Checked by:** A6 and A7 — HARD GATE. The spec marks this rule LLM-assessed; sabotage test D showed A6 alone lets `delete` through, so A7 was added (D7).
**Blind spot:** A7 matches method-name prefixes `create|update|delete|add|remove|save`. A write method named otherwise (e.g. `assign`, `close`) passes → all service write methods must use these prefixes (`coding-conventions`), and the Evaluator keeps "reports is read-only" on its checklist.

## Allowed dependencies at a glance

| Module          | May call (read only)                                                 | Events                                             | Must never                                                      |
| --------------- | -------------------------------------------------------------------- | -------------------------------------------------- | --------------------------------------------------------------- |
| activities      | `StaffService`                                                       | emits `TaskCreatedEvent`, `TaskStatusChangedEvent` | import alerts or reports; write to another module               |
| programmes      | `StaffService`                                                       | —                                                  | import alerts or reports; write to another module               |
| alerts          | —                                                                    | listens to task events                             | call activities' service or repository                          |
| reports         | `ActivityService`, `ProgrammeService`, `StaffService` + their models | —                                                  | call write methods; use repository, controller or dto of others |
| staff           | —                                                                    | —                                                  | depend on any module                                            |
| shared.security | `StaffService.findById` (resolve caller)                             | —                                                  | grow further dependencies on modules                            |

## Test quality (failure mode #3)

A test must assert the business rule, not only the status code. `status().isForbidden()` alone is insufficient; also assert the body (`$.code` is `"FORBIDDEN"`) and the outcome (the task still exists). Coverage thresholds measure quantity, not this → LLM check. Full rules: `how-to-test`.

## Gate integrity

To make a build pass, never:

- edit or remove rules in `ArchitectureTest.java`
- lower JaCoCo thresholds or change gate plugin configuration in `pom.xml`
- change Checkstyle/SpotBugs configuration or add exclusions (JaCoCo exclusions are never allowed; a SpotBugs exclusion needs one pattern, one named class, a reason in `JOURNAL.md`, and explicit approval in the sprint contract)
- weaken a protected test (below), or skip phases (`-DskipTests`, `-Djacoco.skip`)

Evaluator: any diff to these files or patterns without explicit sprint-contract approval = **FAIL**, regardless of score.

### Protected tests (GI-2)

- **Protected:** all test code in `src/test` that exists on `main`: test methods, setup (`@BeforeEach`), helpers and fields. A sprint's tests become protected when that sprint is merged to `main`.
- **Sprint-owned:** test code added on the current sprint branch that is not yet on `main`.
- **Rule:** protected test code is never deleted, `@Disabled`, renamed, reformatted, or has an assertion removed or loosened. Only exception: a change the sprint contract (or its AMENDMENT) names explicitly (e.g. constructor wiring for a new dependency).
- Sprint-owned test code may be changed or removed only to fix an Evaluator finding. Each such change is listed in the generator summary under "Feedback addressed" with `class#method` and the finding ID.
- **Checked by:** `git diff --merge-base main -- src/test`. Every removed (`-`) line is in protected code, because sprint-owned code was never on `main`. Each `-` line must be covered by an explicit contract exception; otherwise GI-2 FAIL.
- **Precondition:** each sprint branch is created from `main` after the previous sprint is merged, and `main` is merged into the sprint branch before each iteration.
- **Blind spot:** changes to sprint-owned tests between iterations are not in this diff. They show only in the generator summary, and a weakened test is caught by re-running TQ-1 to TQ-3 against the ACs.
