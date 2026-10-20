# StoreOps Harness — Architecture Journal

## Decisions

### D1 · 2026-10-17 — Java 21 + Spring Boot stack

- **Decision:** Java 21 + Spring Boot.
- **Alternatives:** Node.js/TypeScript (most detailed scaffold).
- **Rationale:** Daily Java/Spring/AWS experience → skill files can encode real decisions; ArchUnit enables deterministic module-boundary hard gates.
- **Assumption:** Java build/test cycle time is acceptable for the Generator/Evaluator loop.

### D2 · 2026-10-17 — Spring Boot 3.5.x, not 4.x

- **Decision:** Spring Boot 3.5.x.
- **Alternatives:** Spring Boot 4.x (current supported line; 3.x OSS support ended June 2026).
- **Rationale:** Assignment specifies 3.x; Claude Code is more reliable on 3.x; my learning goal is the harness, not framework migration.
- **Assumption:** StoreOps is a non-production reference app, so missing security patches are acceptable. Upgrade to 4.x is a candidate future feature to run through the harness.

### D3 · 2026-10-17 — Sonnet as default model

- **Decision:** Sonnet by default; Opus only for selected high-reasoning tasks.
- **Alternatives:** Opus everywhere.
- **Rationale:** Pro plan usage limits; Opus consumes allowance faster.
- **Assumption:** Sonnet quality is sufficient for generation and fix loops. To revisit: per-agent model choice in the harness.

### D4 · 2026-10-17 — Specification appendix added to bootstrap prompt

- **Decision:** Kept the official prompt verbatim and appended a spec appendix (entities, 9 endpoints, error contract, event bus, ArchUnit/JaCoCo gates).
- **Alternatives:** Use the official prompt as-is.
- **Rationale:** The prompt references "Section 3.6" which Claude Code cannot see; it would have invented endpoints and rules.
- **Assumption:** My appendix accurately reflects PDF Sections 3.3–3.6. Task fields were defined by me, derived from endpoint/feature requirements.

### D5 · 2026-10-18 — Domain events live in shared.event

- **Decision:** `TaskCreatedEvent` and `TaskStatusChangedEvent` (plus `DomainEvent` and `EventBus`) live in `com.storeops.shared.event`; payloads use primitives/Strings (status as its enum name), not activities model types.
- **Alternatives:** An `activities.event` package that alerts reads directly.
- **Rationale:** Alerts would then import `activities.*`, adding a module dependency edge one refactor away from a cycle and weakening the "notifications via event bus only" boundary. With shared events, activities and alerts have no compile-time dependency on each other.
- **Assumption:** Event payloads stay small and stable enough that ids and names are sufficient for alert creation.
- **Known trade-off (found 2026-10-18, alerts review):** Status travels as a String, so AlertService compares against "BLOCKED". Renaming the TaskStatus enum would still compile while escalations silently stop. Mitigation: contract test or shared status constants.

## Observations & corrections log

### 2026-10-18 — Plan-stage corrections (before any code was written)

- Rule 6 contradicted the design: reports allowed only `..service..`, but services return `..model..` types. Fixed: service + model allowed.
- Added anti-gaming policy: no JaCoCo exclusions; SpotBugs exclusions only one pattern/one class, logged here. → Evaluator should check that gate configs weren't weakened.

### 2026-10-18 — Generation result

- `mvnw verify` green: 65 tests, 6 ArchUnit rules, 0 SpotBugs bugs, JaCoCo thresholds met with no exclusions.
- Docker daemon unreachable (old Docker Toolbox env vars) → deferred to deploy phase.
- Dockerfile build skips Checkstyle/SpotBugs/JaCoCo; gates run in `mvnw verify`. → Input for CLAUDE.md CI/CD note.
- Added `.gitattributes`: `mvnw` must keep LF line endings or it breaks on Linux/Docker.

### 2026-10-18 — Choices Claude made where the spec was silent

- New task notifies assignee (or owner if unassigned); status change → BLOCKED raised as ESCALATION.
- Member from another store → 400; programme from another store → 404.
- `Task.withChanges`: null = "unchanged" → assignee can never be cleared.
  → Lesson: the Planner must turn such gaps into explicit acceptance criteria.

### 2026-10-18 — Activities module review: baseline gaps

- No store isolation: list/get/update ignore the caller.
- PATCH has no permission check (delete does).
- programmeId not validated against programmes.
- No status transition rules (DONE → TODO allowed).
- Events are synchronous: an alerts listener failure fails the request after the task is saved.
- `Instant.now()` hard-coded; no injectable Clock → time-based tests (SLA) are hard.

### 2026-10-18 — Alerts module review: baseline gaps

- New-task alerts typed SHIFT_HANDOVER — no fitting AlertType exists (spec gap).
- BLOCKED → "ESCALATION" only notifies owner/assignee, never a STORE_MANAGER.
- Magic string "BLOCKED" — see D5 trade-off.
- Users are notified about their own actions.
- No mark-as-read; EMAIL channel unused.
- Synchronous listener: failure fails the PATCH after the task is saved; retries could duplicate alerts.

### 2026-10-19 — Gate sabotage tests

- A: `throw new IllegalStateException` in `ActivityService.get` → `verify` FAILED on ArchUnit rule 4. ✅
- B: `AlertService` method taking `activities.repository.TaskRepository` → `verify` FAILED on ArchUnit rule 1. ✅
- C: `if (false) { throw new IllegalStateException("x"); }` in `ActivityService.get` → `ArchitectureTest` PASSED — the gate did NOT catch it. javac removes the dead block, so ArchUnit (bytecode analysis) never sees it.
  → Possible fix: add a source-level check (e.g. Checkstyle regex or Evaluator text search for `new (IllegalState|IllegalArgument|Runtime)Exception` in service/controller files), plus an LLM-review checklist item for dead or suspicious code.
- Lesson: automated gates check compiled structure only. Dead code is removed by the compiler, and test _quality_ (failure mode #3) is invisible to tools → the Evaluator needs an LLM review with a strict checklist alongside the hard gates.
