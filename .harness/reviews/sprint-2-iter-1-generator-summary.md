# Generator summary — Sprint 2, iteration 1
Contract: .harness/output/sprint-2-contract.md

## AC self-check
| AC | Met? | Test(s) proving it (class#method) | Note |
|---|---|---|---|
| AC-2.1 | yes | ActivityServiceTest#bulkUpdateBlockedTaskToDoneSavesDoneAndEmitsOneEvent | asserts saved DONE, 1 event BLOCKED->DONE |
| AC-2.2 | yes | ActivityServiceTest#bulkUpdateToInProgressRejectsWholeRequest ; #bulkUpdateToTodoRejectsWholeRequest ; #bulkUpdateWithOneDisallowedTargetRejectsValidItemsToo ; ActivityControllerTest#bulkStatusToInProgressRejectsWholeRequest ; #bulkStatusToTodoRejectsWholeRequest | service: ValidationError, code, no find/save/event; MockMvc: 400, code, message non-empty, T-1 still TODO |
| AC-2.3 | yes | ActivityControllerTest#bulkStatusWithUnknownStatusTextIsMalformedRequest | message exactly "Malformed request" |
| AC-2.4 | yes | ActivityControllerTest#bulkStatusWithMissingStatusRejectsWholeRequest ; #bulkStatusWithBlankTaskIdRejectsWholeRequest | |
| AC-2.5 | yes | ActivityServiceTest#bulkUpdateWithEmptyListRejectsWholeRequest ; #bulkUpdateWithNullListRejectsWholeRequest ; #bulkUpdateWith51ItemsRejectsWholeRequest ; #bulkUpdateWithExactly50ItemsIsAccepted ; ActivityControllerTest#bulkStatusWithEmptyUpdatesRejectsWholeRequest ; #bulkStatusWithMissingUpdatesRejectsWholeRequest ; #bulkStatusWith51ItemsRejectsWholeRequest ; #bulkStatusWithExactly50ItemsIsAccepted | 50 items: updatedCount 50, 50 saves, 50 events |
| AC-2.6 | yes | ActivityServiceTest#bulkUpdateWithDuplicateTaskIdRejectsWholeRequest ; ActivityControllerTest#bulkStatusWithDuplicateTaskIdRejectsWholeRequest | |

## Files changed
| File | Layer (controller/service/repository/model/dto/test) | Change |
|---|---|---|
| src/main/java/com/storeops/activities/dto/BulkStatusItem.java | dto | `@NotBlank taskId`, `@NotNull status` |
| src/main/java/com/storeops/activities/dto/BulkStatusRequest.java | dto | `@NotEmpty @Valid updates` |
| src/main/java/com/storeops/activities/service/ActivityService.java | service | `validateBulkRequest` (empty/null, >50, blank/null fields, duplicate taskId, target not DONE/BLOCKED) called first in `updateStatusBulk`; `MAX_BULK_ITEMS` constant |
| src/test/java/com/storeops/activities/service/ActivityServiceTest.java | test | new tests only (appended) |
| src/test/java/com/storeops/activities/controller/ActivityControllerTest.java | test | new tests and helpers only (appended) |

Controller already had `@Valid` from Sprint 1; unchanged.

## Verify result
`./mvnw verify` → exit 0 (BUILD SUCCESS); tests run 96, failures 0; Checkstyle, JaCoCo, SpotBugs all green. 1 run.

## Gate integrity
Confirmed: no changes to ArchitectureTest.java, pom.xml gate config, Checkstyle/SpotBugs config; no protected test deleted, disabled, renamed, reformatted or weakened. Existing tests untouched; imports added to ActivityServiceTest (ValidationError, ArrayList) only.

## Known gaps
- `isAllowedTransition` still contains the DONE/BLOCKED target check (now unreachable from the bulk path); left in place as it is outside the contract's expected changes.
- Service-level "T-1 remains TODO" is proven by asserting the repository is never read or saved.
