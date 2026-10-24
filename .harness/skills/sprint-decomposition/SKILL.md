---
name: sprint-decomposition
description: How the Planner splits a StoreOps feature into sprints and writes GIVEN/WHEN/THEN acceptance criteria; formats of spec.md and sprint-N-contract.md.
---

# Sprint decomposition

## Purpose

## Inputs

(the feature prompt; app-context incl. known baseline gaps; architecture-principles)

## Sprint boundaries

(what one sprint is: a vertical slice that passes ./mvnw verify on its own;
size limits, e.g. ≤ 6 ACs; order: foundations first, endpoint last;
why each boundary — the reason must be written in spec.md)

## Acceptance criteria

(GIVEN/WHEN/THEN; ID format AC-<sprint>.<n>; what makes one testable:
exact status code, exact body fields, state afterwards, events emitted;
banned words: "properly", "correctly", "appropriate", "should handle";
each AC names its test type: Mockito unit / MockMvc)

## Baseline-gap check

(for each gap in app-context the feature touches: an explicit AC or an explicit "out of scope")

## Open decisions

(questions the Planner must answer in spec.md rather than leave to the Generator)

## spec.md format

(STATUS: AWAITING APPROVAL as first line; feature prompt quoted verbatim; decisions; sprint list)

## sprint-N-contract.md format

(references spec; scope in/out; expected files per layer; ACs; rules R1–R5 that apply;
definition of done)

## Example AC (level of precision — illustrative, response shape not decided)

```
AC-1.3 · Associate cannot update a task they neither own nor are assigned to
GIVEN task T-1 owned by u-lead and assigned to u-lead, status IN_PROGRESS
WHEN u-associate sends PATCH /api/activities/bulk-status with {"taskIds":["T-1"],"status":"DONE"}
THEN the response is 200 and results[0] is {"taskId":"T-1","outcome":"FORBIDDEN"}
AND T-1 remains IN_PROGRESS
AND no TaskStatusChangedEvent is emitted for T-1
Test: MockMvc (ActivityControllerTest) + Mockito verify(eventBus, never())
```
