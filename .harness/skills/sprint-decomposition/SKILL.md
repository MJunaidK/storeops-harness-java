---
name: sprint-decomposition
description: How the Planner turns a StoreOps feature request into an approval-ready spec, vertical-slice sprint contracts, and testable acceptance criteria.
---

# Sprint decomposition

## Purpose

Turn the feature request into a small sequence of independently verifiable vertical slices. The Planner defines observable behavior, design decisions, and boundaries; the Generator implements only an approved sprint contract; the Evaluator checks the contract and repository gates. Do not let implementation details or unresolved product choices be guessed by the Generator.

## Inputs and source of truth

- Read the feature request in full and quote it verbatim in `spec.md`.
- Read `.harness/skills/app-context/SKILL.md` for the current baseline and known gaps, and `.harness/skills/architecture-principles/SKILL.md` for architecture rules and their enforcement.
- Confirm relevant facts against current source, tests, `pom.xml`, and `docs/architecture.md`. Treat skills and diagrams as guides that may drift; source and executable tests determine what the application currently does.
- Use `JOURNAL.md` for recorded design decisions and feature assumptions. Do not treat a recorded demo choice as an approved requirement unless the user or feature prompt adopts it.
- Do not invent endpoints, classes, fields, response shapes, permissions, or existing behavior. Mark uncertainty as an open decision and resolve it before approval.

## Decomposition process

1. Summarize the requested user outcome and identify affected modules, API surfaces, data, events, and architecture rules.
2. Compare the request with the baseline. For each relevant known gap, either define the intended behavior in an acceptance criterion or explicitly mark it out of scope.
3. List decisions needed to make the behavior implementable and testable. Resolve each in `spec.md` with the selected option and rationale; do not leave product or contract decisions to the Generator.
4. Order sprints by dependency and user value. Each sprint is a vertical slice with a coherent, demonstrable outcome, not a layer-only phase. Keep prerequisites in earlier slices only when later slices need them.
5. Aim for no more than six acceptance criteria per sprint. Split larger work by meaningful outcomes, not merely to satisfy the count. State why each boundary exists.
6. Give each sprint a contract that can be implemented and verified without relying on unstated decisions. A sprint is done only when its acceptance criteria pass and `./mvnw verify` exits successfully.

## Acceptance criteria

Write criteria in GIVEN / WHEN / THEN form and assign stable IDs `AC-<sprint>.<number>` (for example, `AC-1.1`). Each criterion must:

- Describe one externally observable behavior or business rule, with enough setup to reproduce it.
- State exact inputs and relevant caller/role, resource state, endpoint, and request body where applicable.
- Specify the expected status, response fields or error code, and resulting persisted state. Include emitted or absent events when they are part of the behavior.
- Name the verification level and likely test location, such as `Mockito unit (ActivityServiceTest)` or `MockMvc (ActivityControllerTest)`. Use both when service behavior and the HTTP contract each need direct coverage.
- Avoid subjective terms such as “properly”, “correctly”, “appropriate”, or “should handle”. Replace them with measurable outcomes.
- Avoid prescribing implementation unless a design constraint requires it; acceptance criteria define behavior, while the sprint contract records implementation boundaries.

Do not require an event assertion for a behavior that emits no event. Do not use coverage percentage as proof that a criterion is tested: the test must assert the behavior and relevant state change.

## Baseline-gap check

For every known gap that the feature touches, record one of:

- **In scope:** acceptance criteria state the intended behavior, including authorization, validation, state changes, and side effects as relevant.
- **Out of scope:** explicitly state that existing behavior remains unchanged and that the gap is not fixed by this feature.

Do not silently broaden the feature to repair unrelated gaps. If a gap blocks the requested behavior, raise it as a dependency or open decision and get it resolved in the spec.

## Open decisions

Resolve decisions that affect the API or event contract, authorization, validation, persistence/state transitions, partial failure, ordering, idempotency, concurrency, or compatibility. For each, record the question, chosen answer, and brief rationale; note material alternatives when useful. A sprint contract must not contain unresolved choices that change acceptance behavior.

## `spec.md` format

Keep `STATUS: AWAITING APPROVAL` as the first line until the user explicitly approves the spec. Include:

1. The feature request quoted verbatim.
2. A concise outcome and affected baseline surfaces.
3. Relevant baseline gaps, each marked in scope with AC references or out of scope.
4. Resolved design decisions and rationale; remaining questions, if any, clearly marked as blocking approval.
5. Ordered sprint list with each sprint's outcome, AC IDs, dependencies, and reason for its boundary.
6. Any assumptions and risks that could change scope or behavior.

Do not start implementation from a draft that is awaiting approval.

## `sprint-N-contract.md` format

Each contract must include:

- Link/reference to the approved `spec.md` and sprint outcome.
- Scope in and out, including relevant baseline gaps explicitly left unchanged.
- Dependencies on earlier sprints and expected files/layers where known; do not invent filenames.
- The sprint's acceptance criteria copied without changing their meaning or IDs.
- Applicable architecture principles R1–R5 and any relevant hard-gate checks, accurately distinguishing automated enforcement from Evaluator review.
- Test expectations tied to the ACs, including state and event assertions where relevant.
- Definition of done: all sprint ACs pass, no unrelated behavior is changed, and `./mvnw verify` exits 0.

## Example criterion

Illustrative only; this describes behavior already represented by the baseline activity-controller test, not a new feature requirement:

```text
AC-1.1 · A non-owner associate cannot delete another user's task
GIVEN task T-1 exists and is owned by u-lead
WHEN u-associate sends DELETE /api/activities/T-1
THEN the response is 403 with code FORBIDDEN
AND T-1 remains retrievable
Test: MockMvc (ActivityControllerTest)
```
