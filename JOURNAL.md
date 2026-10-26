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

### D12 · 2026-10-25 — Severity per review check; SC-2 is blocking

- **Decision:** Every how-to-review check has a fixed severity (B → FAIL, C → at most CONDITIONAL PASS,
  M → score only). Scope-out tests (SC-2) are blocking.
- **Alternatives:** Reviewer judgement per finding (my manual Sprint 1 verdict: CONDITIONAL PASS).
- **Rationale:** The same Sprint 1 findings gave CONDITIONAL PASS by hand and FAIL by my first written marks —
  proof that unwritten judgement is not deterministic. Scope-out tests lock in behaviour a later sprint must
  change, forcing an edit to an existing test (forbidden by GI-2); the fix is cheap, so block it early.
- **Assumption:** Blocking findings are fixable within 3 iterations; if SC-2 causes repeated FAILs, revisit.

### D13 · 2026-10-25 — Severity decides the verdict; the weighted score measures trend

- **Decision:** Verdict = integrity → hard sub-gates → B checks → C checks; weighted score (Arch 40 /
  Functional 40 / Quality 20) is reported every time, and total < 80 caps the verdict at CONDITIONAL PASS.
- **Alternatives:** Score-only verdict (PASS ≥ 80, CONDITIONAL 65–79, FAIL < 65), as in many rubrics.
- **Rationale:** A score-only rule lets many small passes hide one serious failure (e.g. a scope-out test);
  severities make serious findings decisive regardless of totals. The score still gives the Monitor a
  comparable number per sprint for trend notes.
- **Assumption:** With only one M check (TQ-5) the 80 threshold cannot trigger today (worst case 96); it
  becomes active only if more M checks are added. Revisit if the checklist grows.

### D14 · 2026-10-25 — Worked examples in skills use invented names, never real code under review

- **Decision:** Examples in evaluator-facing skills (grading-criteria, how-to-review) use invented class,
  method and test names that do not exist in the codebase.
- **Alternatives:** Real examples from the current sprint (more concrete, easier to follow).
- **Rationale:** A real example leaks the expected findings: on Sprint 1 the evaluator searched for the
  exact names from the example instead of applying the checklist, so the run could not show whether
  the checklist works.
- **Assumption:** Invented examples still teach the rules well enough; verify by re-running sprint 1
  and checking that the evaluator still finds SC-2 and TQ-4 on its own.

### D15 · 2026-10-25 — Protected tests = test code on main; review diff = working tree vs merge base

- **Decision:** Test code that exists on `main` is protected (methods, setup, helpers, fields); test code added
  on the sprint branch is sprint-owned and may be changed or removed only to fix an Evaluator finding, listed
  in the generator summary. Defined once in architecture-principles "Protected tests"; how-to-test,
  how-to-review (GI-2) and generator.agent.md refer to it. The Evaluator diffs with `git diff --merge-base main`.
- **Alternatives:** (a) Literal GI-2 ("no existing test changed"): deadlock, because the SC-2 fix requires
  removing a test the Generator added in iteration 1. (b) File-level protection (only new test files editable):
  the scope-out test was a new method inside the existing `ActivityServiceTest.java`, so it still deadlocks.
  (c) Any test change allowed if justified in the summary: needs LLM judgement and makes weakening tests easy.
- **Rationale:** In a diff against the merge base, every removed (`-`) line is protected code, because
  sprint-owned code was never on main. The check stays mechanical: no `-` lines in `src/test` unless the
  contract names the change explicitly (e.g. Sprint 3 constructor wiring).
- **Assumption:** Each sprint branch starts from `main` after the previous sprint is merged, and `main` is merged
  into the sprint branch before each iteration. Changes to sprint-owned tests between iterations are not in
  the diff; they are covered by the summary and by re-running TQ-1 to TQ-3.
- **Consequence:** A sprint's tests become protected when it merges to `main`. Who merges, and when the next
  branch is created, is still open (→ D16, CLAUDE.md routing).

### D16 · 2026-10-25 — The orchestrator commits, merges and branches; it never pushes

- **Decision:** After every Evaluator verdict the orchestrator commits the sprint branch (code + that iteration's
  archives, verdict in the message). On PASS or CONDITIONAL PASS it merges `sprint-N` into local `main` and creates
  `sprint-<N+1>` from `main`. It never pushes; the developer pushes after the run. On escalation: no merge.
- **Alternatives:** (b) the developer merges after every sprint: a human check before main, but a manual step the
  PDF excludes (§2.1). (c) merge once at the end: Sprint 2 could weaken Sprint 1's tests, because tests become
  protected only on `main` (D15).
- **Rationale:** The loop stays autonomous; the Evaluator verdict is the gate before `main`; the push is the human
  checkpoint before the remote and CI. Per-iteration commits make FAIL → fix visible in history and expose changes
  to sprint-owned tests (D15 blind spot).
- **Assumption:** A local `main` that is not yet pushed can be reset if a run goes wrong. The main session is allowed
  only specific git commands, never push or reset.
- **Consequence:** Archives are committed on the sprint branch and reach `main` by merge; how-to-review step 2
  excludes `.harness/reviews` from the review diff. (Supersedes "archives live on main" from the 2026-10-25 log.)

### D17 · 2026-10-25 — Routing rules: CONDITIONAL PASS advances; unreadable verdict gets one re-run; three escalation triggers

- **Decision:** PASS and CONDITIONAL PASS end the sprint (merge); FAIL retries up to iteration 3. The iteration
  number is counted from the archived feedback files. An invalid verdict line triggers one Evaluator re-run, then
  escalation. Escalation triggers: E1 3rd FAIL, E2 verdict invalid twice, E3 a git step fails.
  escalation.md offers the developer RETRY / AMEND / STOP.
- **Alternatives:** CONDITIONAL PASS sent back to the Generator (C findings become blocking in practice, contradicting
  D12); an invalid verdict treated as FAIL (the Generator would get broken feedback); iteration count kept in the
  session (lost when the session restarts).
- **Rationale:** Every routing input is a fact the orchestrator can read (first line, file count, git exit status),
  so the same files always give the same next step.
- **Assumption:** The Evaluator writes the verdict line correctly almost every time, so E2 is rare. Contract
  amendments stay a developer action; the orchestrator never edits contracts.

### D18 · 2026-10-26 — Monitor: archive-only, Haiku, fixed check→skill signal rules

- **Decision:** The Monitor reads only the sprint's archives, usage-log and earlier run logs (no code), has no
  Bash, runs on Haiku, and flags skill-file drift by fixed rules (S1 same check fails twice in a sprint, S2 a check
  fails again across sprints, S3 3 iterations or escalation, S4 verdict re-run) mapped to the skill file that owns
  the check. Durations and tokens come from the orchestrator; missing numbers are "n/r".
- **Alternatives:** Sonnet (better prose, higher cost for a summarizing task); Monitor reads code (duplicates the
  Evaluator); free-form trend notes (not comparable across sprints).
- **Rationale:** The run log must be comparable sprint to sprint, so its signals follow rules, not judgement.
  First per-agent model choice (D3 revisit): the Evaluator is the most expensive agent (47% of 24h usage), the
  Monitor should be the cheapest.
- **Assumption:** Haiku reliably fills a fixed table format from structured files. If run logs come out
  incomplete or wrong, switch to Sonnet and record it.

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

### 2026-10-24 — Gate sabotage test G (Checkstyle)

- G: star import `org.springframework.aop.aspectj.*` in `ActivityService` → `.\mvnw.cmd verify` FAILED in 7 s
  on Checkstyle `AvoidStarImport` (validate phase, before compile and tests). ✅
- The violation is reported as `[WARNING]` yet fails the build: `checkstyle.xml` sets every rule to `warning`,
  but `pom.xml` has `failOnViolation=true` + `violationSeverity=warning` → every warning is a violation.
- Lesson: the rule file alone suggested Checkstyle was a soft gate; only the plugin config showed it is a hard
  gate. Verify a gate by reading all of its configuration and by breaking it.

### 2026-10-24 — Usage before Sprint 1

- Planner ≈ 74% of 24h usage (3 runs: spec v1, spec v2, contracts); weekly 21% used before Sprint 1.
- Claude Code suggested a cheaper model for planner — not applied: spec quality drives every later sprint.
  Revisit after the run with real numbers (D3).

### 2026-10-24 — Usage after Sprint 1

- Weekly 21% → 22% (≈ 1% for Generator sprint 1, iteration 1); session window ≈ 7%; $0.86 API-equivalent; ~20 min.
- 24h share by subagent: planner 46%, generator 34%. Planner (3 runs) cost more than one Generator iteration.
- D3 revisit: keep Sonnet for both. Cost is low enough that a cheaper planner model is not worth the risk to
  spec quality; 3 sprints with retries fit comfortably before the weekly reset.

### 2026-10-24 — Generator sprint 1, iteration 1 (19m 47s; reports verify green, 78 tests)

- Wrote/edited Java via Bash heredocs + a Python script instead of Write/Edit → hidden from accept-edits
  diffs, fragile, needs Python. Fix: generator.agent.md Never-list: files only via Write/Edit; Bash only
  for ./mvnw and read-only commands.
- Method name: contract (DEC-10) says `bulkUpdateStatus` and claims it keeps the A7 write prefix — it
  doesn't (starts with "bulk", so A7 would not catch a call from reports). Generator used `updateStatusBulk`
  and reported the deviation. Root cause: unverified claim in the spec, missed by Planner and by my review.
  → Evaluator check: names in contract vs code; Planner check: new write methods must start with a D7 prefix.
- Ran verify as `./mvnw -q verify 2>&1 | tail -40; echo exit=$?` → $? is tail's exit code, so a failed build
  reports exit=0. Later run used `| grep … BUILD` (accurate). → Evaluator must run verify itself;
  generator.agent.md: never pipe verify; report mvnw's own result.
- Added test `bulkUpdateWithNullItemsListIsEmptyResponse` (null list → 200 empty) and maps TODO/IN_PROGRESS
  targets to per-item CONFLICT — both contradict Sprint 2 (whole request → 400). Locks in Scope-out behaviour;
  Sprint 2 must change an existing test. → Evaluator check: tests must not assert Scope-out behaviour.
- Approved each `./mvnw verify` prompt individually; did not use "don't ask again for ./mvnw verify \*"
  (the wildcard would also allow -DskipTests).

### 2026-10-24 — Sprint 1 manual evaluation (before the Evaluator exists)

- Re-ran `.\mvnw.cmd verify` myself: BUILD SUCCESS, 78 tests, Checkstyle 0, ArchUnit 7/7, SpotBugs 0, JaCoCo met.
  Generator's claim confirmed.
- Cost of Sprint 1: see "Usage after Sprint 1".
- Findings: F1 method name (contract wrong — amend), F2 scope-out placeholder behaviour + test (amend Sprint 2
  contract), F3 tautological `updatedAt` assertion (minor; AC still proven by `never().save`), F4 process
  (Bash edits, piped verify — generator.agent.md fixed).
- My verdict: CONDITIONAL PASS — but I applied an unwritten rule. Another reviewer could call F2 a FAIL.
  → Evaluator design: gates (deterministic) + yes/no checklist (how-to-review) + fixed severity and verdict
  rule (grading-criteria).
- `sprint-1` branch kept unmerged until the Evaluator agent has run on it (demo needs evaluator-feedback.md
  and run-log.md from the agents).

### 2026-10-25 — Evaluator sprint 1, iteration 1: leakage from the worked example

- The evaluator grep'd for the exact test and assertion named in grading-criteria's worked example
  (`bulkUpdateWithNullItemsListIsEmptyResponse`, `assertThat(t2.updatedAt()).isEqualTo(before)`)
  → leakage: the example gave away Sprint 1's findings, so this run cannot prove the checklist works.
- Fix: make the worked example generic (invented names); re-run on Sprint 1 for a fair test.

### 2026-10-25 — Evaluator sprint 1, iteration 1: clean re-run after D14

- Same verdict and findings as the leaked run, found independently: FAIL on SC-2 (B)
  (ActivityServiceTest.java:331; also BulkStatusRequest.java:8), TQ-4 (C) (ActivityServiceTest.java:314),
  CC-3 PASS (deviation listed with D7 reason). Scores 100/82/100, total 93. 7m 14s.
- New vs leaked run: traced SC-2 to spec DEC-2 and gave a concrete fix instruction.
- Matches my manual review (F2 → SC-2, F3 → TQ-4) → checklist + grading rules validated on real code.

### 2026-10-25 — Usage after Evaluator sprint 1 (2 runs: leaked + clean)

- Weekly 25% → 26% for both runs (includes chat in the same window → upper bound); session window reset
  mid-run, not comparable. Subagent shares (24h): generator 42%, evaluator 36%.
- One evaluation ≈ 7 min and < 1% weekly; one full iteration (Generator + Evaluator) ≈ 1–1.5% weekly
  → Sprints 2–3 with retries fit before the weekly reset. Claude Code again suggested a cheaper generator
  model — not applied (D3/D13 reasoning holds).

### 2026-10-25 — Archive location and review diff base

- Sprint 1 iteration 1 archives had been committed on `sprint-1`, so the Evaluator saw them in its diff and
  had to explain them away (SH-2). Moved them to `main` (`d69b0cc`); all archives now live on `main`.
- Proposed `git diff main...HEAD` so later harness commits on `main` would not appear in the review diff.
  Wrong: the Generator never commits, and `...HEAD` compares commits only, so the Evaluator would miss all
  of the iteration's work. `git diff --merge-base main` compares the working tree with the merge base: it
  sees uncommitted work and ignores later commits on `main`. Verified on `main`: it listed my uncommitted edits.
- Lesson: before choosing a diff command, check where each agent leaves its work (committed or working tree).

### 2026-10-25 — Sprint 1 iteration 2 via the orchestrator: PASS

- First start ran in auto mode → aborted after ~3 min. The Generator had already edited exactly the two files
  named in the feedback; partial edits discarded with `git restore src`. Lesson: check the permission mode first.
- Restarted in manual mode. The orchestrator computed iteration 2 from the archives (ignored the `-LEAKED` file).
- Generator 5m 52s: removed the scope-out test, the true-by-construction assertion and the null placeholder.
  Ran `./mvnw verify` without pipes (generator.agent.md fix from iteration 1 held).
- Evaluator 12m 47s: first tried `./mvnw verify 2>&1 | grep … | head` chained with git commands. I rejected it;
  without any extra message it re-ran `./mvnw verify` exactly as written. → A plain rejection works as feedback.
- Verdict PASS, 77 tests (78 − removed test). The orchestrator archived, committed `d4faca9`
  ("sprint-1 iter-2: PASS") and merged to main with `--no-ff` (`1b9e6d6`); no push; Monitor skipped (not built).
- Orchestrator bundled verdict read, archive, commit, switch and merge into one command and piped the merge
  through `tail`. No harm (main was contained in sprint-1, so no conflict was possible), but a conflict would have
  been hidden. → CLAUDE.md git rules: one command per call, no pipes.
- Pattern: Generator, Evaluator and orchestrator all reached for pipes. Instructions reduce it but don't stop it.
  → Candidate improvement: deny rules in `.claude/settings.json` (tool-enforced, not instruction-enforced).
- Evaluator note: `bulkUpdateToCurrentStatusIsConflictAndLeavesUpdatedAtUnchanged` no longer asserts `updatedAt`;
  the name overstates the test. No check covers "test name matches assertions" — idea, not acted on.
- 2026-10-26: Sprint 2 amendment updated after the PASS — the test-rewrite allowance removed (test no longer exists); null/missing/empty updates → 400; "no existing test may be changed" (D15: Sprint 1 tests are now protected).

### 2026-10-26 — Sprint 2 via the orchestrator: PASS on iteration 1

- Fully autonomous: branch, Generator (7m 23s, 63k tokens), Evaluator (2m 30s, 48k tokens), archive, commit,
  merge (`--no-ff`), Monitor (22s, Haiku), run log commit, stop. 96 tests, all scores 100.
- First sprint with protected tests (D15): GI-2 evidence "no `-` lines" — Sprint 1's tests untouched.
- Orchestrator used the broad `git add .harness/reviews`: my CLAUDE.md edit for exact file names had not been
  saved before the "adds exact files" commit. Harmless this time (nothing else in the folder); fixed now.
- Evaluator evidence was thin on TQ-4 and SC-2 (result without saying what was checked) — evidence rule not
  fully applied. Watch in Sprint 3; tighten the rule if it repeats.
- Disclosed open item: `isAllowedTransition` keeps a now-unreachable target check. No check covers dead
  branches (AR-2 covers dead throws only) — idea, not acted on.

### 2026-10-26 — Sprint 3 via the orchestrator: PASS on iteration 1; demonstration run complete

- Generator 3m 27s (79k tokens), Evaluator 3m 03s (53k), Monitor 22s (28k, Haiku). 114 tests, all scores 100.
- Named-file `git add` worked (CLAUDE.md fix); my uncommitted usage-log.md stayed out of the commits.
- D15 shaped the design: the Generator kept a 2-arg `BulkStatusItem` constructor rather than edit protected
  tests (0 deletions in test files). The contract's wiring exception was not needed — still untested live.
- Evaluator evidence thin again (TQ-4, SC-2 "none found"; AC-3.4 roles not named) — 2 sprints in a row.
  The Monitor cannot see weak PASS evidence; its signals fire only on FAILs. → REFLECTION candidate.
- Whole feature (3 sprints, 4 iterations): ≈ 4–5% of the weekly Pro allowance, ≈ $3.30 API-equivalent.
