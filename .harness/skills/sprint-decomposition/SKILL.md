---
name: sprint-decomposition
description: How the Planner turns a StoreOps feature request into an approval-ready spec, vertical-slice sprint contracts, and testable GIVEN/WHEN/THEN acceptance criteria.
---

# Sprint decomposition

## Purpose

Turn a feature request into a small sequence of independently verifiable vertical slices. The Planner defines observable behaviour, design decisions and boundaries; the Generator implements only an approved sprint contract; the Evaluator checks that contract plus the repository gates. No product or contract choice may be left for the Generator to guess.

## Inputs (read only these)

1. The feature prompt (from the developer's `@planner` message; also stored in `PROMPT.md`).
2. `.harness/skills/app-context/SKILL.md`: modules, real class names, seed users, **known baseline gaps**.
3. `.harness/skills/architecture-principles/SKILL.md`: rules R1–R5 and how each is checked (A1–A7).
4. Source of the **affected module only** (controller, service, model, dto) and its existing tests, to confirm current behaviour. Do not read other modules unless the feature calls them.

Do not read `JOURNAL.md`, `.harness/reviews/` or unrelated modules: they cost context and are not requirements. If a skill file contradicts the source, the source wins: note the contradiction in `spec.md` under Risks.

Never invent endpoints, classes, fields, response shapes, permissions or existing behaviour. Mark uncertainty as an open decision and resolve it before approval.

## Outputs and approval

- Write `.harness/output/spec.md` with `STATUS: AWAITING APPROVAL` as its first line, then **stop**. Do not write contracts or code before approval.
- When the developer replies `APPROVED`, change the first line to `STATUS: APPROVED` and write one `.harness/output/sprint-<N>-contract.md` per sprint.
- If the developer replies with changes instead, revise `spec.md`, keep `STATUS: AWAITING APPROVAL`, and stop again.

## Decomposition process

1. Summarise the user outcome and identify affected modules, endpoints, models, events and rules R1–R5.
2. Run the **baseline-gap check** (below).
3. List and resolve the **open decisions** (below), each with an ID `DEC-<n>`.
4. Order sprints by dependency and user value. Each sprint is a vertical slice with a demonstrable outcome (request in, behaviour out), not a layer-only phase such as "repository changes".
5. Size: at most **6 ACs per sprint** and normally **2–3 sprints per feature**. Each Generator↔Evaluator loop allows 3 iterations; small sprints fail cheaper. Split by meaningful outcome, not to hit a count, and write the reason for every boundary in `spec.md`.
6. **Every sprint must leave `./mvnw verify` green on its own**, including JaCoCo thresholds (service ≥ 80%, controller ≥ 70%). Tests for a sprint's ACs therefore belong in that sprint; never defer tests to a later sprint.

## Acceptance criteria

Format: GIVEN / WHEN / THEN (AND …), ID `AC-<sprint>.<n>` (e.g. `AC-2.3`), one behaviour per AC. Each AC must:

- **GIVEN:** name the caller using seed users (`u-manager` STORE_MANAGER, `u-lead` DEPARTMENT_LEAD, `u-associate` ASSOCIATE, `u-regional` REGIONAL_MANAGER) and the exact starting state (task status, owner, assignee).
- **WHEN:** give the exact HTTP method, path, `X-User-Id` header and request body, or the exact service call for unit-level ACs.
- **THEN:** give the exact status code; the exact response fields; for errors, the `code` value of the `AppError` subclass (`ForbiddenError` → 403, `NotFoundError` → 404, `ValidationError` → 400, `ConflictError` → 409, `UnauthorizedError` → 401); the **state afterwards** (e.g. "T-1 remains IN_PROGRESS"); and events emitted **or explicitly not emitted** when side effects are part of the behaviour.
- **Test:** name the level and class: `Mockito unit (ActivityServiceTest)` for business rules, `MockMvc (ActivityControllerTest)` for the HTTP contract. Use both when the rule and the HTTP mapping each need direct coverage.
- Never use subjective words: "properly", "correctly", "appropriate", "should handle", "gracefully". Replace each with a measurable outcome.
- Describe behaviour, not implementation; implementation boundaries go in the sprint contract.

**StoreOps test data:** seed data contains only `store-1`. An AC about store isolation must state that its test creates data for a second store (e.g. `store-2`) in the test setup; it cannot be demonstrated with seed data alone.

Coverage is not evidence that an AC is tested: the test must assert the AC's response and its state change.

## Baseline-gap check

For every gap in app-context's "Known baseline gaps" table that the feature touches, record in `spec.md` one of:

- **In scope:** the AC IDs that define the intended behaviour.
- **Out of scope:** "existing behaviour unchanged; gap not fixed by this feature".

Gaps most features touch: tasks not store-scoped, PATCH has no permission check, no status transition rules, assignee cannot be cleared, events synchronous, status sent as String in events. Do not silently repair unrelated gaps. If a gap blocks the feature, make it an open decision.

## Open decisions

Resolve every choice that changes behaviour the Evaluator will test. For StoreOps features these usually include:

- **Transitions:** which `TaskStatus` changes are allowed (TODO, IN_PROGRESS, DONE, BLOCKED)?
- **Authorization:** which roles, owner or assignee may perform the action; behaviour for other stores' data.
- **Errors:** which `AppError` subclass for each failure, and per-item vs whole-request failure.
- **Response shape:** exact JSON fields and status code, including partial success.
- **Events:** which `DomainEvent` is emitted per change (D9: cross-module side effects only via `EventBus`), and which `AlertType` alerts should produce.
- **Persistence:** where new data lives (inside the owning module per R1) and whether it is exposed via an endpoint.
- **Limits:** batch size, duplicates, empty input, idempotency.

Record each as `DEC-<n>`: question → chosen answer → one-line rationale (and the rejected alternative when it matters). A sprint contract must not contain an unresolved decision.

## `spec.md` format

```
STATUS: AWAITING APPROVAL
# Spec: <feature name>
## Feature prompt          (verbatim, as in PROMPT.md)
## Outcome                 (2–4 lines)
## Affected surfaces       (modules, endpoints, classes, events, rules R1–R5)
## Baseline gaps           (gap → in scope: AC-x.y | out of scope)
## Decisions               (DEC-1 … DEC-n)
## Sprints                 (per sprint: outcome, AC IDs, dependencies, reason for boundary)
## Acceptance criteria     (all ACs, grouped by sprint)
## Assumptions and risks
```

## `sprint-N-contract.md` format

```
# Sprint <N> contract: <outcome>
Spec: .harness/output/spec.md (APPROVED)   Decisions applied: DEC-…
## Scope in / Scope out    (incl. baseline gaps explicitly left unchanged)
## Expected changes        (layer → existing file to modify or new class to add; real names only)
## Acceptance criteria     (copied verbatim from spec.md, same IDs)
## Architecture rules      (which of R1–R5 apply; hard gate A-rule vs Evaluator review)
## Test expectations       (per AC: test class, and the state/event assertions required)
## Definition of done      (all ACs pass; no unrelated behaviour changed; ./mvnw verify exits 0;
                            no gate files changed — see architecture-principles "Gate integrity")
```

Traceability is checked: `spec.md` quotes the prompt; each contract references the spec and its `DEC` IDs; the Generator's summary reports per AC ID; the Evaluator's feedback references AC IDs and files.

## Example criterion

Illustrative only: it describes behaviour already in the baseline, not a new requirement.

```text
AC-1.1 · A non-owner associate cannot delete another user's task
GIVEN task T-1 exists, owned by u-lead, status TODO
WHEN u-associate sends DELETE /api/activities/T-1 with header X-User-Id: u-associate
THEN the response is 403 with body code "FORBIDDEN"
AND GET /api/activities/T-1 by u-lead still returns 200
AND no event is emitted
Test: MockMvc (ActivityControllerTest)
```
