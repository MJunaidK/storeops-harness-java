---
name: app-context
description: What StoreOps is — modules, entities, naming, dependencies, commands, known gaps. Read by every agent before acting.
---

# StoreOps — Application Context

## Purpose of this file

Shared facts about the StoreOps codebase, read by every harness agent (Planner, Generator, Evaluator, Monitor) before acting, so no agent needs to explore the code to learn its shape. Rules and how they are enforced live in `architecture-principles`, not here.

## Domain

REST API for retail store operations. Store teams create **tasks** (restocking, planogram resets, audits, compliance checks), group them into store **programmes** (seasonal rollouts, compliance drives, refits), receive in-app **alerts** when tasks change, and (later) view **reports** of task completion by store and region. Staff belong to one store and have a role: REGIONAL_MANAGER > STORE_MANAGER > DEPARTMENT_LEAD > ASSOCIATE.

## Tech stack & commands

- Java 21, Spring Boot 3.5.x, Maven via wrapper. In-memory storage only (ConcurrentHashMap), no database.
- **Hard gate:** `./mvnw verify` must exit 0 — runs Checkstyle, compile, tests (incl. ArchUnit), JaCoCo thresholds, SpotBugs. `./mvnw test` is NOT sufficient (skips JaCoCo and SpotBugs).
- Fast architecture-only check: `./mvnw test -Dtest=ArchitectureTest`
- Run: `./mvnw spring-boot:run` → port 8080. Example:
  `curl -H "X-User-Id: u-manager" http://localhost:8080/api/activities`

## Package layout

- `com.storeops.<module>.{controller, service, repository, model, dto}`
- `com.storeops.shared.error` — `AppError` + `NotFoundError` 404, `ValidationError` 400, `UnauthorizedError` 401, `ForbiddenError` 403, `ConflictError` 409; `GlobalExceptionHandler` → `{"code","message"}`
- `com.storeops.shared.event` — `EventBus.emit(DomainEvent)`, `TaskCreatedEvent`, `TaskStatusChangedEvent`
- `com.storeops.shared.security` — `AuthenticatedUser(id, storeId, role)`, resolved from `X-User-Id`
- Repository = interface + `InMemory*Repository`. Models and DTOs are Java records.
- Tests in `src/test/java` mirror the main packages; `ArchitectureTest` holds the ArchUnit rules.

## Modules

| Module     | Responsibility                                                   | Key types (actual class names)                                                                                                                                            | Endpoints                                                           |
| ---------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| activities | Store tasks: create, assign, update status, delete               | `Task`, `TaskStatus` (TODO, IN_PROGRESS, DONE, BLOCKED), `TaskPriority` (LOW, MEDIUM, HIGH, CRITICAL), `TaskCategory` (RESTOCKING, PLANOGRAM, AUDIT, COMPLIANCE, GENERAL) | GET/POST `/api/activities`, GET/PATCH/DELETE `/api/activities/{id}` |
| programmes | Store programmes and their members, scoped to the caller's store | `Project`, `ProjectMember`, `ProjectRole`                                                                                                                                 | GET/POST `/api/programmes`, POST `/api/programmes/{id}/members`     |
| alerts     | Notifications created in reaction to task events                 | `Notification`, `AlertType`, `NotificationChannel`, `NotificationStatus`                                                                                                  | GET `/api/alerts`                                                   |
| staff      | Read-only staff directory and identity                           | `User`, `UserProfile`, `StaffRole`, `AuthToken`                                                                                                                           | **None** (auth only)                                                |
| reports    | Read-only aggregation across modules (skeleton)                  | `Report`, `ReportType`, `ReportStatus`                                                                                                                                    | **None yet** (demo feature)                                         |

**Naming:** "programme" in the API and domain = `Project*` classes in code. Do not create `Programme`/`ProgrammeMember` classes or `/projects` endpoints. "Activity" = `Task`.

## Module dependencies

- activities → `StaffService` (read: validate assignee); emits events via `EventBus`
- programmes → `StaffService` (read: validate member and store)
- reports → `ActivityService`, `ProgrammeService`, `StaffService` (read only) + their `model` types
- alerts → nothing; reacts to `TaskCreatedEvent` / `TaskStatusChangedEvent` via `@EventListener`
- staff → nothing (most depended-on module)
- Every request: `shared.security` resolver → `StaffService.findById`
- `Task.programmeId` links tasks to programmes by **ID only** (never validated). Tasks have no `storeId`; store comes via the programme.
- Diagram: `docs/architecture.md`

## Authentication stub & seed data

Header `X-User-Id` identifies the caller; missing/unknown → 401. Seeded users (all in `InMemoryUserRepository`):

| id            | name           | role             | storeId                           | department |
| ------------- | -------------- | ---------------- | --------------------------------- | ---------- |
| `u-manager`   | Maria Manager  | STORE_MANAGER    | store-1                           | Management |
| `u-lead`      | Dan Lead       | DEPARTMENT_LEAD  | store-1                           | Grocery    |
| `u-associate` | Alex Associate | ASSOCIATE        | store-1                           | Grocery    |
| `u-regional`  | Rita Regional  | REGIONAL_MANAGER | `region-1` (stored as a store id) | Region     |

Only one real store exists (store-1), so cross-store behaviour cannot be observed with seed data alone.

## Known baseline gaps

Planner: if a feature touches one of these, state the intended behaviour as an explicit acceptance criterion. Generator: do not silently copy or "fix" a gap outside the sprint contract.

| Gap                                  | Where                                           | Implication for features                                                     |
| ------------------------------------ | ----------------------------------------------- | ---------------------------------------------------------------------------- |
| Tasks not store-scoped               | `ActivityService.list/get/update` ignore caller | Any task query or aggregation sees all stores                                |
| PATCH has no permission check        | `ActivityService.update`                        | Bulk/status updates must define who may change what                          |
| No status transition rules           | `Task.withChanges`                              | DONE→TODO etc. allowed; handover/bulk features must define valid transitions |
| Assignee cannot be cleared           | `Task.withChanges` (null = unchanged)           | "Unassign" needs an explicit design                                          |
| `programmeId` not validated          | `ActivityService.create`                        | Tasks can reference non-existent programmes                                  |
| No injectable `Clock`                | `Instant.now()` in services                     | Time-based logic (SLA, overdue) is untestable until a Clock is injected      |
| No region model                      | `User.storeId = "region-1"`                     | Regional rollups need a store→region mapping first                           |
| Departments only on `UserProfile`    | `Task` has no department                        | Per-department metrics are not computable                                    |
| Alert types incomplete               | New task → `SHIFT_HANDOVER`                     | Choose/add AlertTypes explicitly                                             |
| "Escalation" never reaches a manager | `AlertService`                                  | SLA escalation must look up STORE_MANAGER via `StaffService`                 |
| Events carry status as String        | `AlertService` compares `"BLOCKED"`             | Renaming enum values silently breaks listeners                               |
| Events are synchronous               | `@EventListener`                                | A listener failure fails the originating request                             |
| Programmes have no lifecycle         | `Project` has no status                         | "Programme closes" cannot be triggered                                       |
