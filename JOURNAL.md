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

### D6 · 2026-10-21 — Hard-gate command is `./mvnw verify`, not `mvn test`

- **Decision:** All gates run via `./mvnw verify` using the Maven wrapper.
- **Alternatives:** `mvn test` (PDF's generic Java command).
- **Rationale:** `test` skips JaCoCo thresholds and SpotBugs (bound to verify); the wrapper pins the Maven version across laptop, Docker, CI and the harness.
- **Assumption:** The verify phase stays fast enough for up to 3 Evaluator iterations per sprint.

### D7 · 2026-10-22 — ArchUnit rule 7: reports may not call write methods on other modules' services

- **Decision:** Add ArchUnit rule 7: classes in reports must not call methods named create*/update*/delete*/add*/remove*/save* on other modules' service classes.
- **Alternatives:** Leave rule 6 as is and rely only on the Evaluator's LLM checklist for "read-only reports".
- **Rationale:** Sabotage test D proved rule 6 lets `activityService.delete(...)` through; create/update are blocked only by accident (they need a DTO). A deterministic gate beats an LLM judgment for a rule this clear-cut.
- **Assumption:** Write methods on services follow the naming convention. A write method with another name (e.g. `assign`, `close`) would bypass the rule → coding-conventions must require these prefixes, and the LLM checklist keeps a "reports is read-only" item as backup.

### D8 · 2026-10-22 — Harden ArchUnit rule 4 from blacklist to whitelist

- **Decision:** Controllers and services may construct only Throwables that are subclasses of `AppError`
  (rule renamed `CONTROLLERS_AND_SERVICES_CONSTRUCT_ONLY_APP_ERRORS`).
- **Alternatives:** Keep the blacklist of 4 types (RuntimeException, IllegalArgumentException,
  IllegalStateException, Exception) and catch other exceptions via the LLM checklist.
- **Rationale:** The blacklist let `UnsupportedOperationException`, `NoSuchElementException` and home-made
  `extends RuntimeException` classes through. Rule 4 is the core gate for failure mode #2, so it should
  forbid everything except the allowed types, not list a few forbidden ones.
- **Assumption:** Scope stays controller + service packages (matches the spec). Raw exceptions in model,
  repository or dto classes, and dead code removed by javac (sabotage C), remain LLM-review items.

### D9 · 2026-10-23 — Cross-module reads are direct calls; cross-module writes are events

- **Decision:** A module may call read methods (get*/find*/list*/require*) on another module's
  service. It must never call write methods (create*/update*/delete*/add*/remove*/save*) on another
  module's service; cross-module side effects are emitted as events via `EventBus` and handled by
  the owning module's `@EventListener`.
- **Alternatives:** (a) Events for everything, including reads — impractical: a read needs a synchronous
  answer, so this would require request/reply messaging or copying data between modules.
  (b) Allow direct write calls (e.g. programmes → `ActivityService.create`) — simpler, but couples
  modules and is exactly client failure mode #4.
- **Rationale:** Matches PDF §3.5: Module boundary says "cross-module reads go through the target
  module's service layer only"; Event bus only says "side effects that cross module boundaries must be
  raised via the event bus". The baseline already follows it: every cross-module call is a read.
- **Assumption:** Write methods follow the D7 naming prefixes. Events are synchronous in-process
  (`@EventListener`), so a listener's write runs in the same request — acceptable for now.
- **Consequence:** e.g. planogram templates → programmes emits an event; activities listens and
  creates the tasks itself. Enforcement today: A5 (partial) + LLM checklist. Possible future gate:
  widen A7 from reports to all modules.

### D10 · 2026-10-23 — Demo feature: shift handover bulk update

- **Decision:** Use "Add shift handover bulk update" for the demonstration run: `PATCH /api/activities/bulk-status`
  lets outgoing shift staff mark multiple tasks as DONE or BLOCKED in one request, with partial failure handling
  and an audit entry per updated task (PDF §3.4).
- **Alternatives:**
  (a) SLA breach alerting — needs an injectable `Clock`, a scheduler and a configurable grace period; none exist,
  and time-based tests are harder to make deterministic.
  (b) Regional rollup report — needs a store→region model that does not exist (Rita is stored with
  `storeId = "region-1"`), plus store scoping in activities first.
  (c) Planogram task template — needs a department on `Task` (only on `UserProfile` today) and a cross-module
  write from programmes to activities, which D9 requires to go through a new event.
- **Rationale:** All changes stay inside the activities module and reuse the existing `TaskStatusChangedEvent`;
  nothing missing from the baseline must be built first. It still forces the Planner to settle real design
  questions explicitly instead of leaving them to the Generator: valid status transitions, who may update which
  tasks, the partial-failure response shape, where audit entries live, and whether each task emits its own event.
  Several of these are documented baseline gaps (no transition rules, no PATCH permission check, no store scoping).
- **Assumption:** The feature fits in 2–3 sprints, and audit entries can be stored within activities without a new
  module. If either proves false during the run, revisit this decision.

### D11 · 2026-10-23 — Agents run as Claude Code subagents via thin wrappers in .claude/agents/

- **Decision:** Agent definitions stay in `.harness/agents/*.agent.md` (PDF §5.2/§7). For each agent,
  `.claude/agents/<name>.md` is a thin wrapper: frontmatter only (`name`, `description`, `tools`, `model`)
  plus one instruction to read and follow `.harness/agents/<name>.agent.md`. `CLAUDE.md` starts each agent
  as a subagent and passes only the inputs it needs.
- **Alternatives:**
  (a) `CLAUDE.md` only: the main session reads the agent file and plays each role itself. Simplest, but
  there is no context reset between agents and the tool limits in the frontmatter are not enforced.
  (b) Move the definitions into `.claude/agents/`: native subagents with no extra files, but breaks the
  required `.harness/agents/` structure.
- **Rationale:** Claude Code only discovers subagents in `.claude/agents/`, where frontmatter `tools` and
  `model` are enforced and each subagent runs in its own fresh context window (checked in Claude Code docs,
  2026-10-23). Wrappers keep the PDF structure as the single source of truth, give each agent least-privilege
  tools, and make the context reset between agents structural: the Evaluator never sees the Generator's
  reasoning, only its files.
- **Assumption:** The wrapper's single instruction is followed reliably, so the definition in `.harness/agents/`
  is what the agent actually uses. Risk: tools/model can drift between the wrapper and the definition's
  frontmatter, so the wrapper is the one that counts and the two must be kept identical.
- **Consequence:** A subagent runs once and ends; it cannot wait for `APPROVED`. The Planner therefore runs
  twice (run 1: `spec.md`; run 2: contracts), with the developer's approval in the main session in between.

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

### 2026-10-19 — Programmes module review: baseline gaps

- No permission checks: any associate can create a programme and add members.
- Two unconnected role systems: StaffRole (job) vs ProjectRole (programme role) — an ASSOCIATE can be added as programme STORE_MANAGER.
- Name mismatch: module is "programmes" but classes are Project/ProjectMember/ProjectRole (inherited from the PDF) → skill files must state the mapping.
- Project has no status/lifecycle and emits no events — yet the spec expects "programme closes → STORE_SUMMARY report".
- addMember is not thread-safe: read-check-save race can silently lose a member.
- Creator is recorded (createdBy) but not automatically added as a member.
- Key insight: programmes IS store-scoped, activities is NOT — the prompt specified store scope only for programmes. The Generator built exactly what was specified, nothing more → the Planner's acceptance criteria must state scope rules explicitly.

### 2026-10-20 — Staff module review: baseline gaps

- **Missing Region Concept**: Regions are currently treated like normal stores (e.g., Rita the regional manager has `storeId = "region-1"`), and there is no way to link which stores belong to which region. A regional rollup feature cannot be built until this relationship is defined.
- **Single Store Limitation**: The app currently only seeds one store (`store-1`) for every user. Because of this, store isolation in activities cannot be tested, and any cross-store bugs are completely hidden.
- **Disconnected Departments**: Departments are only stored in the `UserProfile`, while tasks have no department field. This makes it impossible to calculate metrics like "overdue counts by department" or run `DEPARTMENT_PERFORMANCE` reports.
- **Dead Auth Code**: `AuthToken` and `tokenFor()` are never used. The app relies on a header stub for authentication, and since tokens aren't stored, they cannot be validated.
- **Confusing Error Codes**: Invalid references (such as creating a task for a non-existent assignee) trigger a `404 Not Found` error via `requireById` instead of a clearer `400 Bad Request`, making it look like the endpoint itself is missing.

### 2026-10-20 — Reports module review: baseline gaps

- **Mixed Scoping in Reports**: The report mixes data from different scopes—programmes and staff are correctly counted for the user's specific store, but tasks are fetched globally via `activityService.list(null, null)` from all stores. This causes a store summary to display one store's staff alongside every store's tasks. One missing rule upstream (activities scoping) produced wrong numbers downstream. Gaps propagate across modules.
- **Ignored Report Type Parameter**: The report type parameter is completely ignored, meaning `STORE_SUMMARY`, `REGIONAL_ROLLUP`, and `DEPARTMENT_PERFORMANCE` all return the exact same metrics.
- **Hardcoded Status**: Report statuses are always set to `READY`, as `PENDING` and `FAILED` are never used because reports are generated instantly within the same request.
- **Unenforced "Read-Only" Logic**: "Read-only" is currently just a convention rather than an enforced rule. Because rule 6 allows service dependencies, a report could theoretically call mutating methods on `ActivityService` without an ArchUnit rule catching it, demonstrating why tool checks alone aren't enough (reference in Evaluator's checklist and Design Brief Section C).

### 2026-10-21 — app-context first draft

- First draft contained invented class names (Activity, Programme, Staff), wrong endpoint claims
  (staff/reports), and a dependency table that contradicted the code.
- Lesson: a skill file with wrong facts is worse than none — agents trust it. Every name, path and
  endpoint in a skill file must be verified against the code.
- → Evaluator idea: a check that class names referenced in skill files actually exist in src/.

### 2026-10-22 — Gate sabotage tests (rule 6)

- D: `activityService.delete(caller, id)` in `ReportService` → `ArchitectureTest` PASSED — gate did NOT catch it.
- E: `activityService.update(id, new UpdateTaskRequest(null, null, null, null))` in `ReportService` → FAILED on ArchUnit rule 6. ✅
- Lesson: ArchUnit checks which types/packages are used, not which methods are called. create/update are blocked only because they need a DTO (forbidden package); delete needs none → "read-only reports" is not enforced by rule 6. → D7.

### 2026-10-22 — Hardened gates verified

- F before hardening: `UnsupportedOperationException` in ActivityService → PASSED (blacklist gap confirmed).
- After D7/D8: D → FAILED on rule 7 ✅; F → FAILED on rule 4 ✅. `./mvnw verify` green on baseline.

### 2026-10-23 — architecture-principles draft review: lessons

- First draft repeated the app-context problem: wrong "Checked by" rule numbers, R3 still described the pre-D8 blacklist, and R2 ("no direct calls between modules") would have failed correct baseline code.
- Five patterns to carry into the next project:
  1. Verify every name against the source: a name in a skill file is a claim, and agents trust it.
  2. Test each rule against the baseline: if correct code breaks the rule, the rule is wrong.
  3. Every rule needs how it's checked + its blind spot, or it can't be enforced consistently.
  4. Delete generic text: if a sentence fits any project, it adds nothing (test-quality section).
  5. Re-read the spec table column by column: specific prohibitions (AlertService/ReportService imports, HTTP types in repositories) were missing.
- Fix: ArchUnit tests renamed A1–A7 in skill files to keep them distinct from architecture rules R1–R5.
- → Candidate for REFLECTION.md: skill files drift from code and spec; nothing checks them automatically.

### 2026-10-24 — Agent token and time consumption

- Per-agent token count is visible only while the agent runs (e.g. planner revise ≈ 40.3k tokens, 1m 13s);
  after completion it disappears from the CLI → Monitor cannot measure cost itself; orchestrator must
  capture and pass it, or the Monitor estimates.
- Planner run 2 (contracts) took 8m 8s.

### 2026-10-24 — Planner phase completed (first real run)

- Flow worked end to end: `@planner` → planner subagent → spec v1 → change request → spec v2 → APPROVED →
  3 sprint contracts. Every step traced to a harness file (CLAUDE.md, wrapper, planner.agent.md, 3 skills).
- Change request (4 items: audit endpoint STORE_MANAGER-only, one behaviour per AC, class name in Risks,
  verify error codes) was applied correctly; status stayed AWAITING APPROVAL. The Planner found and fixed a
  real error: v1 assumed "Malformed request" for every invalid case.
- Spec grew from 2 to 3 sprints (6 ACs each). Sprint 2 boundary is partly count-driven (BLOCKED → DONE
  placed in the validation sprint) — accepted trade-off.
- Contracts followed the format; one invented method name (`listAudit`) accepted as naming-only.
- Spec versions and contracts archived manually to `.harness/reviews/` (output/ is gitignored).
- Claude Code 2.1.310 removed the `/agents` wizard; verified subagent discovery by asking Claude.
