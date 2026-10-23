---
name: architecture-principles
description: The non-negotiable StoreOps architecture rules, why each exists, how each is checked. Read by Planner, Generator and Evaluator.
---

# StoreOps — Architecture Principles

## Purpose

## Hard gate

(./mvnw verify exit 0; what it runs; ./mvnw test is not enough)

## Rules

### R1 · Module boundary (failure mode #1)

**Rule:** All modules must respect their boundaries; no direct access to internal classes of other modules.

**Why:** Violating module boundaries creates tight coupling, making the system harder to maintain and evolve.

**Correct:**
import com.storeops.activities.model.Task; // public API
**Violation:**
import com.storeops.activities.repository.TaskRepository; // internal class
**Mapping:** internal classes are only accessible within the same module.
**Checked by:** ArchUnit rule 1 (`./mvnw verify`) — HARD GATE.

### R2 · Event bus only (failure mode #4)

**Rule:** All inter-module communication must go through the event bus; no direct method calls between modules.

**Why:** Direct calls create tight coupling and make it harder to evolve modules independently.

**Correct:**
eventBus.emit(new TaskCreatedEvent(saved.id(), saved.title(), saved.ownerId(), saved.assigneeId(), now));
**Violation:**
otherModuleService.someMethod(); // direct call

**Mapping:** all cross-module interactions must be via events.
**Checked by:** ArchUnit rule 2 (`./mvnw verify`) — HARD GATE.

### R3 · Error contract (prevents failure mode #2)

**Rule:** Services and controllers throw only `AppError` subclasses from `com.storeops.shared.error`.
Never construct `RuntimeException`, `IllegalArgumentException`, `IllegalStateException` or `Exception`.

**Why:** Raw exceptions bypass `GlobalExceptionHandler`, so clients get a 500 with no `{"code","message"}`
body instead of the intended 400/403/404/409.

**Correct:**
return taskRepository.findById(id).orElseThrow(() -> new NotFoundError("Task not found: " + id));
**Violation:**
return taskRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("not found"));

**Mapping:** missing → NotFoundError 404 · invalid input/reference → ValidationError 400 ·
no permission → ForbiddenError 403 · duplicate → ConflictError 409 · no/unknown caller → UnauthorizedError 401.
Need a new kind? Add a subclass of AppError in shared.error — never a raw exception.

**Checked by:** ArchUnit rule 4 (`./mvnw verify`) — HARD GATE.
**Blind spot:** ArchUnit reads compiled bytecode; dead code (`if (false) throw …`) is removed by javac
and not detected (verified 2026-10-19, sabotage test C). Evaluator must also scan the diff source.

### R4 · Layer separation

**Rule:** Each layer (e.g., controller, service, repository) must only interact with the layer directly below it; no skipping layers.

**Why:** Skipping layers creates tight coupling and makes it harder to maintain and evolve the system.

**Correct:**
controller → service → repository
**Violation:**
controller → repository // skipping the service layer

**Mapping:** each layer can only call the layer directly below it.
**Checked by:** ArchUnit rule 3 (`./mvnw verify`) — HARD GATE.

### R5 · Read-only reports

**Rule:** Report services must only read data from other modules; they must never modify it.

**Why:** Modifying data from a read-only perspective can lead to unexpected side effects and violate the principle of separation of concerns.

**Correct:**
List<Task> tasks = activityService.list(null, null); // read-only
**Violation:**
activityService.create(caller, request); // modifying data from a read-only service

**Mapping:** report services can only call read methods of other services.
**Checked by:** ArchUnit rule 5 (`./mvnw verify`) — HARD GATE.

## Allowed dependencies at a glance

activities["activities<br/>Store tasks"]
programmes["programmes<br/>Store initiatives"]
reports["reports<br/>Read-only aggregation"]
staff["staff<br/>Read-only directory"]
bus(["shared.event<br/>EventBus"])
alerts["alerts<br/>Notification inbox"]

    activities -->|"requireById"| staff
    programmes -->|"requireById"| staff
    reports -->|"list"| activities
    reports -->|"listForStore"| programmes
    reports -->|"listByStore"| staff
    activities -.->|"TaskCreatedEvent<br/>TaskStatusChangedEvent"| bus
    bus -.->|"@EventListener"| alerts
    activities -. "programmeId (ID only, not validated)" .- programmes

## Test quality (failure mode #3)

The test quality section emphasizes that tests should not only pass but also effectively catch potential violations of architectural principles.
Tests should be designed to fail when a rule is violated, not just to verify correct behavior.
This ensures that the tests are effective in enforcing architectural principles and catching regressions.

**How to test:**

- Write tests that deliberately violate each architectural rule and verify that the tests fail.
- Use ArchUnit to automatically enforce layer separation and read-only report rules.
- Regularly update tests to cover new scenarios and edge cases.
- Ensure that all architectural rules have corresponding tests that can catch violations.
- Regularly review and update tests to reflect changes in the architecture and new rules.
- Ensure that test coverage is comprehensive and that no architectural rule is left untested.
- Regularly analyze test failures to identify patterns and areas for improvement.
- Continuously improve the test suite based on feedback and evolving architectural principles.
- Ensure that the test suite is well-documented and that developers understand the purpose and usage of each test.
- Regularly review and update documentation to reflect changes in the test suite and architectural principles.
- Ensure that the test suite is integrated into the CI/CD pipeline to catch violations early and consistently.
- Regularly review and update the CI/CD pipeline to reflect changes in the test suite and architectural principles.

## Gate integrity

(never edit ArchitectureTest rules, JaCoCo thresholds, Checkstyle/SpotBugs config, or add exclusions
to make a build pass; any change to these files = automatic FAIL unless the sprint contract requires it)
