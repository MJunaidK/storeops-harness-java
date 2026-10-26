# Generator summary — Sprint 3, iteration 1
Contract: .harness/output/sprint-3-contract.md

## AC self-check
| AC | Met? | Test(s) proving it (class#method) | Note |
|---|---|---|---|
| AC-3.1 | yes | ActivityServiceTest#bulkUpdateSavesOneAuditEntryPerUpdatedTask ; ActivityControllerTest#auditListsOneEntryPerUpdatedTaskForManager | |
| AC-3.2 | yes | ActivityServiceTest#failedItemsAndRejectedRequestsSaveNoAuditEntry ; ActivityControllerTest#auditIsEmptyAfterFailedItemsAndRejectedRequest | |
| AC-3.3 | yes | ActivityServiceTest#listAuditReturnsRepositoryEntriesOldestFirst ; ActivityServiceTest#sequentialBulkUpdatesSaveEntriesInOrder ; ActivityControllerTest#auditEntriesAreListedOldestFirst | |
| AC-3.4 | yes | ActivityServiceTest#listAuditIsForbiddenForEveryRoleExceptStoreManager ; ActivityServiceTest#listAuditByNonManagerForUnknownTaskIsForbiddenNotNotFound ; ActivityControllerTest#auditIsForbiddenForEveryRoleExceptStoreManager ; ActivityControllerTest#auditOfUnknownTaskByNonManagerIsForbiddenNotNotFound | |
| AC-3.5 | yes | ActivityControllerTest#auditOfMissingTaskIsNotFoundForManager ; ActivityServiceTest#listAuditOfUnknownTaskIsNotFoundForManager | |
| AC-3.6 | yes | ActivityServiceTest#bulkUpdateWithNoteOver500CharactersRejectsWholeRequest ; ActivityServiceTest#bulkUpdateWithOneLongNoteRejectsValidItemsToo ; ActivityServiceTest#bulkUpdateWithNoteOfExactly500CharactersIsAccepted ; ActivityControllerTest#bulkStatusWithNoteOver500CharactersRejectsWholeRequest ; ActivityControllerTest#bulkStatusWithNoteOfExactly500CharactersIsAccepted | |

## Files changed
| File | Layer (controller/service/repository/model/dto/test) | Change |
|---|---|---|
| src/main/java/com/storeops/activities/model/AuditEntry.java | model | New record |
| src/main/java/com/storeops/activities/repository/AuditRepository.java | repository | New interface (save, findByTaskId) |
| src/main/java/com/storeops/activities/repository/InMemoryAuditRepository.java | repository | New in-memory impl (CopyOnWriteArrayList, insertion order) |
| src/main/java/com/storeops/activities/dto/BulkStatusItem.java | dto | Added `note` with `@Size(max = 500)`; kept 2-arg convenience constructor so existing callers/tests are unchanged |
| src/main/java/com/storeops/activities/service/ActivityService.java | service | AuditRepository injected; audit entry saved after task save; `listAudit` (role then existence); note length check in `validateBulkRequest` |
| src/main/java/com/storeops/activities/controller/ActivityController.java | controller | `GET /{id}/audit` -> `listAudit` |
| src/test/java/com/storeops/activities/service/ActivityServiceTest.java | test | `@Mock AuditRepository` (constructor wiring) + new AC-3.x tests |
| src/test/java/com/storeops/activities/controller/ActivityControllerTest.java | test | New AC-3.x tests (additions only) |

## Verify result
`./mvnw verify` -> exit 0 (BUILD SUCCESS); tests run 114, failures 0; Checkstyle 0 violations, SpotBugs 0, JaCoCo checks met. One run.

## Gate integrity
No changes to ArchitectureTest.java, pom.xml gate config, Checkstyle/SpotBugs config. No protected test deleted, disabled, renamed, reformatted or weakened. Only change to existing test code: added `@Mock AuditRepository` field in ActivityServiceTest (constructor wiring for the new dependency, as the contract allows).

## Known gaps
None. Assumption: the audit entry is saved after the task save and before the `TaskStatusChangedEvent` emit. No separate InMemoryAuditRepository test was needed (JaCoCo green via the MockMvc tests).
