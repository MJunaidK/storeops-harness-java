# StoreOps

Retail store operations REST API. Java 21, Spring Boot 3, Maven. In-memory storage, no database required.

## Claude Code development harness

This repository contains the StoreOps API and the harness that built its "shift handover bulk update" feature.

- **Start a run:** `@planner <feature prompt>` in Claude Code (see `CLAUDE.md`); demo prompt in `PROMPT.md`.
- **Harness:** `CLAUDE.md` (orchestrator) · `.harness/agents/` (4 agents) · `.harness/skills/` (7 skills).
- **Demonstration run:** `.harness/reviews/` (index in its README) · `DEPLOYMENT.md`.
- **Documents:** `DESIGN_BRIEF.md` · `REFLECTION.md` · `JOURNAL.md` (decisions D1–D19).
- **Gates:** `./mvnw verify` (Checkstyle, tests, ArchUnit, JaCoCo, SpotBugs).

## Run

```bash
./mvnw verify            # build + Checkstyle, SpotBugs, ArchUnit, tests, JaCoCo gates
./mvnw spring-boot:run   # start on http://localhost:8080
```

Docker:

```bash
docker compose up --build
```

## Auth stub

Every endpoint requires an `X-User-Id` header resolved against seeded in-memory staff.
Missing or unknown user returns `401`.

| User id       | Role             | Store    |
| ------------- | ---------------- | -------- |
| `u-manager`   | STORE_MANAGER    | store-1  |
| `u-lead`      | DEPARTMENT_LEAD  | store-1  |
| `u-associate` | ASSOCIATE        | store-1  |
| `u-regional`  | REGIONAL_MANAGER | region-1 |

```bash
curl -H "X-User-Id: u-manager" http://localhost:8080/api/activities
```

## Endpoints

| Method | Path                           | Notes                                                                |
| ------ | ------------------------------ | -------------------------------------------------------------------- |
| GET    | `/api/activities`              | optional `programmeId`, `status`                                     |
| POST   | `/api/activities`              | creates a task, emits `TaskCreatedEvent`                             |
| GET    | `/api/activities/{id}`         |                                                                      |
| PATCH  | `/api/activities/{id}`         | `status`, `priority`, `category`, `assigneeId`                       |
| DELETE | `/api/activities/{id}`         | task owner or STORE_MANAGER only, otherwise `403`                    |
| PATCH  | `/api/activities/bulk-status`  | 1–50 items to DONE/BLOCKED; per-item results; audit entry per update |
| GET    | `/api/activities/{id}/audit`   | STORE_MANAGER only, otherwise `403`; oldest first                    |
| GET    | `/api/programmes`              | programmes of the caller's store                                     |
| POST   | `/api/programmes`              |                                                                      |
| POST   | `/api/programmes/{id}/members` | `409` if already a member                                            |
| GET    | `/api/alerts`                  | notifications of the caller                                          |

Errors are JSON: `{"code": "NOT_FOUND", "message": "..."}` (`VALIDATION_ERROR` 400, `UNAUTHORIZED` 401, `FORBIDDEN` 403, `CONFLICT` 409).

## Architecture

Modules: `activities`, `programmes`, `staff` (auth only, no endpoints), `alerts`, `reports` (skeleton, no endpoints).
Each has `controller → service → repository` plus `model` and `dto`. Shared code is in `com.storeops.shared.{error,event,security}`.

Rules enforced by `ArchitectureTest` (part of `./mvnw verify`):

1. Only module X touches `com.storeops.X.repository`.
2. Controllers never access repositories.
3. No cycles between modules.
4. Controllers and services construct only `AppError` subclasses — any other `Throwable` is forbidden (whitelist, D8).
5. `activities` and `programmes` do not depend on `alerts` or `reports`.
6. `reports` depends on other modules only through their `service` and `model` packages.
7. `reports` never calls write methods (`create*`, `update*`, `delete*`, `add*`, `remove*`, `save*`) on other modules' services (D7).

Alerts are created by `@EventListener`s reacting to domain events published through `EventBus`.
