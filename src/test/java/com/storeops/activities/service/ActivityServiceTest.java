package com.storeops.activities.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.storeops.activities.dto.BulkStatusItem;
import com.storeops.activities.dto.BulkStatusRequest;
import com.storeops.activities.dto.BulkStatusResponse;
import com.storeops.activities.dto.CreateTaskRequest;
import com.storeops.activities.dto.UpdateTaskRequest;
import com.storeops.activities.model.AuditEntry;
import com.storeops.activities.model.Task;
import com.storeops.activities.model.TaskCategory;
import com.storeops.activities.model.TaskPriority;
import com.storeops.activities.model.TaskStatus;
import com.storeops.activities.repository.AuditRepository;
import com.storeops.activities.repository.TaskRepository;
import com.storeops.shared.error.ForbiddenError;
import com.storeops.shared.error.NotFoundError;
import com.storeops.shared.error.ValidationError;
import com.storeops.shared.event.EventBus;
import com.storeops.shared.event.TaskCreatedEvent;
import com.storeops.shared.event.TaskStatusChangedEvent;
import com.storeops.shared.security.AuthenticatedUser;
import com.storeops.staff.model.StaffRole;
import com.storeops.staff.service.StaffService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final AuthenticatedUser MANAGER = new AuthenticatedUser("m1", "s1", StaffRole.STORE_MANAGER);
    private static final AuthenticatedUser OWNER = new AuthenticatedUser("o1", "s1", StaffRole.ASSOCIATE);
    private static final AuthenticatedUser OTHER = new AuthenticatedUser("x1", "s1", StaffRole.ASSOCIATE);

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private StaffService staffService;
    @Mock
    private EventBus eventBus;
    @Mock
    private AuditRepository auditRepository;
    @InjectMocks
    private ActivityService service;

    private Task task(TaskStatus status) {
        Instant now = Instant.now();
        return new Task("t1", "p1", "Restock aisle 4", "d", status, TaskPriority.LOW, TaskCategory.RESTOCKING, "a1", "o1", null,
            now, now);
    }

    @Test
    void listDelegatesFiltersToRepository() {
        when(taskRepository.findAll("p1", TaskStatus.DONE)).thenReturn(List.of(task(TaskStatus.DONE)));

        assertThat(service.list("p1", TaskStatus.DONE)).hasSize(1);
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        when(taskRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("nope")).isInstanceOf(NotFoundError.class).hasMessageContaining("nope");
    }

    @Test
    void createAppliesDefaultsAndEmitsTaskCreatedEvent() {
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateTaskRequest request = new CreateTaskRequest("p1", "Audit", null, null, null, "a1", null);

        Task created = service.create(OWNER, request);

        assertThat(created.status()).isEqualTo(TaskStatus.TODO);
        assertThat(created.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(created.category()).isEqualTo(TaskCategory.GENERAL);
        assertThat(created.ownerId()).isEqualTo("o1");
        verify(staffService).requireById("a1");
        ArgumentCaptor<TaskCreatedEvent> captor = ArgumentCaptor.forClass(TaskCreatedEvent.class);
        verify(eventBus).emit(captor.capture());
        assertThat(captor.getValue().taskId()).isEqualTo(created.id());
        assertThat(captor.getValue().assigneeId()).isEqualTo("a1");
    }

    @Test
    void createKeepsExplicitPriorityAndCategoryWithoutAssignee() {
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        CreateTaskRequest request = new CreateTaskRequest("p1", "Audit", "d", TaskPriority.CRITICAL, TaskCategory.AUDIT, null, null);

        Task created = service.create(OWNER, request);

        assertThat(created.priority()).isEqualTo(TaskPriority.CRITICAL);
        assertThat(created.category()).isEqualTo(TaskCategory.AUDIT);
        verify(staffService, never()).requireById(any());
    }

    @Test
    void updateChangingStatusEmitsStatusChangedEvent() {
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task(TaskStatus.TODO)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service.update("t1", new UpdateTaskRequest(TaskStatus.BLOCKED, null, null, null));

        assertThat(updated.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(updated.priority()).isEqualTo(TaskPriority.LOW);
        ArgumentCaptor<TaskStatusChangedEvent> captor = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventBus).emit(captor.capture());
        assertThat(captor.getValue().oldStatus()).isEqualTo("TODO");
        assertThat(captor.getValue().newStatus()).isEqualTo("BLOCKED");
    }

    @Test
    void updateWithoutStatusChangeEmitsNothingAndValidatesAssignee() {
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task(TaskStatus.TODO)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task updated = service.update("t1", new UpdateTaskRequest(null, TaskPriority.HIGH, TaskCategory.AUDIT, "a2"));

        assertThat(updated.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(updated.category()).isEqualTo(TaskCategory.AUDIT);
        assertThat(updated.assigneeId()).isEqualTo("a2");
        verify(staffService).requireById("a2");
        verify(eventBus, never()).emit(any());
    }

    @Test
    void deleteByOwnerSucceeds() {
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task(TaskStatus.TODO)));

        service.delete(OWNER, "t1");

        verify(taskRepository).deleteById("t1");
    }

    @Test
    void deleteByStoreManagerSucceeds() {
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task(TaskStatus.TODO)));

        service.delete(MANAGER, "t1");

        verify(taskRepository).deleteById("t1");
    }

    @Test
    void deleteByNonOwnerAssociateIsForbidden() {
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task(TaskStatus.TODO)));

        assertThatThrownBy(() -> service.delete(OTHER, "t1")).isInstanceOf(ForbiddenError.class);
        verify(taskRepository, never()).deleteById(any());
    }

    private Task bulkTask(String id, String ownerId, String assigneeId, TaskStatus status) {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        return new Task(id, "p1", "Title " + id, "d", status, TaskPriority.HIGH, TaskCategory.AUDIT, assigneeId, ownerId, null,
            created, created);
    }

    private BulkStatusRequest bulk(BulkStatusItem... items) {
        return new BulkStatusRequest(List.of(items));
    }

    // AC-1.1
    @Test
    void bulkUpdateMixedItemsUpdatesAllAndEmitsTwoEvents() {
        Task t1 = bulkTask("T-1", "m1", "u1", TaskStatus.TODO);
        Task t2 = bulkTask("T-2", "m1", "u1", TaskStatus.IN_PROGRESS);
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(t1));
        when(taskRepository.findById("T-2")).thenReturn(Optional.of(t2));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        AuthenticatedUser assignee = new AuthenticatedUser("u1", "s1", StaffRole.ASSOCIATE);

        BulkStatusResponse response = service.updateStatusBulk(assignee,
            bulk(new BulkStatusItem("T-1", TaskStatus.DONE), new BulkStatusItem("T-2", TaskStatus.BLOCKED)));

        assertThat(response.updatedCount()).isEqualTo(2);
        assertThat(response.failedCount()).isZero();
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).taskId()).isEqualTo("T-1");
        assertThat(response.results().get(0).success()).isTrue();
        assertThat(response.results().get(0).status()).isEqualTo("DONE");
        assertThat(response.results().get(0).errorCode()).isNull();
        assertThat(response.results().get(0).message()).isNull();
        assertThat(response.results().get(1).taskId()).isEqualTo("T-2");
        assertThat(response.results().get(1).status()).isEqualTo("BLOCKED");
        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).status()).isEqualTo(TaskStatus.DONE);
        assertThat(saved.getAllValues().get(1).status()).isEqualTo(TaskStatus.BLOCKED);
        for (Task s : saved.getAllValues()) {
            assertThat(s.assigneeId()).isEqualTo("u1");
            assertThat(s.priority()).isEqualTo(TaskPriority.HIGH);
            assertThat(s.category()).isEqualTo(TaskCategory.AUDIT);
        }
        ArgumentCaptor<TaskStatusChangedEvent> events = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventBus, times(2)).emit(events.capture());
        assertThat(events.getAllValues().get(0).taskId()).isEqualTo("T-1");
        assertThat(events.getAllValues().get(0).oldStatus()).isEqualTo("TODO");
        assertThat(events.getAllValues().get(0).newStatus()).isEqualTo("DONE");
        assertThat(events.getAllValues().get(1).taskId()).isEqualTo("T-2");
        assertThat(events.getAllValues().get(1).oldStatus()).isEqualTo("IN_PROGRESS");
        assertThat(events.getAllValues().get(1).newStatus()).isEqualTo("BLOCKED");
    }

    // AC-1.2
    @Test
    void bulkUpdateMissingTaskFailsOnlyItsOwnItem() {
        when(taskRepository.findById("T-404")).thenReturn(Optional.empty());
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(bulkTask("T-1", "m1", "u1", TaskStatus.TODO)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkStatusResponse response = service.updateStatusBulk(MANAGER,
            bulk(new BulkStatusItem("T-404", TaskStatus.DONE), new BulkStatusItem("T-1", TaskStatus.DONE)));

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results().get(0).taskId()).isEqualTo("T-404");
        assertThat(response.results().get(0).success()).isFalse();
        assertThat(response.results().get(0).status()).isNull();
        assertThat(response.results().get(0).errorCode()).isEqualTo("NOT_FOUND");
        assertThat(response.results().get(0).message()).isEqualTo("Task not found: T-404");
        assertThat(response.results().get(1).success()).isTrue();
        assertThat(response.results().get(1).status()).isEqualTo("DONE");
        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo("T-1");
        assertThat(saved.getValue().status()).isEqualTo(TaskStatus.DONE);
        ArgumentCaptor<TaskStatusChangedEvent> events = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventBus).emit(events.capture());
        assertThat(events.getValue().taskId()).isEqualTo("T-1");
    }

    // AC-1.3
    @Test
    void bulkUpdateByUnrelatedAssociateIsForbiddenPerItem() {
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(bulkTask("T-1", "lead", "lead", TaskStatus.TODO)));
        AuthenticatedUser stranger = new AuthenticatedUser("assoc", "s1", StaffRole.ASSOCIATE);

        BulkStatusResponse response = service.updateStatusBulk(stranger, bulk(new BulkStatusItem("T-1", TaskStatus.DONE)));

        assertThat(response.updatedCount()).isZero();
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results().get(0).taskId()).isEqualTo("T-1");
        assertThat(response.results().get(0).success()).isFalse();
        assertThat(response.results().get(0).status()).isEqualTo("TODO");
        assertThat(response.results().get(0).errorCode()).isEqualTo("FORBIDDEN");
        assertThat(response.results().get(0).message()).isNotEmpty();
        verify(taskRepository, never()).save(any());
        verify(eventBus, never()).emit(any());
    }

    // AC-1.4
    @Test
    void bulkUpdateByStoreManagerSucceedsOnForeignTask() {
        when(taskRepository.findById("T-3")).thenReturn(Optional.of(bulkTask("T-3", "lead", "lead", TaskStatus.TODO)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkStatusResponse response = service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-3", TaskStatus.BLOCKED)));

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(response.results().get(0).taskId()).isEqualTo("T-3");
        assertThat(response.results().get(0).success()).isTrue();
        assertThat(response.results().get(0).status()).isEqualTo("BLOCKED");
        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(TaskStatus.BLOCKED);
        ArgumentCaptor<TaskStatusChangedEvent> events = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventBus).emit(events.capture());
        assertThat(events.getValue().taskId()).isEqualTo("T-3");
        assertThat(events.getValue().oldStatus()).isEqualTo("TODO");
        assertThat(events.getValue().newStatus()).isEqualTo("BLOCKED");
    }

    // AC-1.5
    @Test
    void bulkUpdateOfDoneTaskIsConflict() {
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(bulkTask("T-1", "m1", "u1", TaskStatus.DONE)));

        BulkStatusResponse response = service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-1", TaskStatus.BLOCKED)));

        assertThat(response.updatedCount()).isZero();
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results().get(0).success()).isFalse();
        assertThat(response.results().get(0).status()).isEqualTo("DONE");
        assertThat(response.results().get(0).errorCode()).isEqualTo("CONFLICT");
        assertThat(response.results().get(0).message()).isNotEmpty();
        verify(taskRepository, never()).save(any());
        verify(eventBus, never()).emit(any());
    }

    // AC-1.6
    @Test
    void bulkUpdateToCurrentStatusIsConflictAndLeavesUpdatedAtUnchanged() {
        when(taskRepository.findById("T-2")).thenReturn(Optional.of(bulkTask("T-2", "m1", "u1", TaskStatus.BLOCKED)));

        BulkStatusResponse response = service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-2", TaskStatus.BLOCKED)));

        assertThat(response.updatedCount()).isZero();
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results().get(0).success()).isFalse();
        assertThat(response.results().get(0).status()).isEqualTo("BLOCKED");
        assertThat(response.results().get(0).errorCode()).isEqualTo("CONFLICT");
        assertThat(response.results().get(0).message()).isNotEmpty();
        verify(taskRepository, never()).save(any());
        verify(eventBus, never()).emit(any());
    }

    @Test
    void bulkUpdateAllowsBlockedToDone() {
        when(taskRepository.findById("T-2")).thenReturn(Optional.of(bulkTask("T-2", "m1", "u1", TaskStatus.BLOCKED)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkStatusResponse response = service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-2", TaskStatus.DONE)));

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.results().get(0).status()).isEqualTo("DONE");
    }

    // AC-2.1
    @Test
    void bulkUpdateBlockedTaskToDoneSavesDoneAndEmitsOneEvent() {
        when(taskRepository.findById("T-4")).thenReturn(Optional.of(bulkTask("T-4", "m1", "u1", TaskStatus.BLOCKED)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkStatusResponse response = service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-4", TaskStatus.DONE)));

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(response.results().get(0).taskId()).isEqualTo("T-4");
        assertThat(response.results().get(0).success()).isTrue();
        assertThat(response.results().get(0).status()).isEqualTo("DONE");
        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo("T-4");
        assertThat(saved.getValue().status()).isEqualTo(TaskStatus.DONE);
        ArgumentCaptor<TaskStatusChangedEvent> events = ArgumentCaptor.forClass(TaskStatusChangedEvent.class);
        verify(eventBus, times(1)).emit(events.capture());
        assertThat(events.getValue().taskId()).isEqualTo("T-4");
        assertThat(events.getValue().oldStatus()).isEqualTo("BLOCKED");
        assertThat(events.getValue().newStatus()).isEqualTo("DONE");
    }

    private void assertRejectedWholeRequest(BulkStatusRequest request) {
        assertThatThrownBy(() -> service.updateStatusBulk(MANAGER, request))
            .isInstanceOf(ValidationError.class)
            .satisfies(e -> assertThat(((ValidationError) e).getCode()).isEqualTo("VALIDATION_ERROR"))
            .satisfies(e -> assertThat(e.getMessage()).isNotEmpty());
        verify(taskRepository, never()).findById(any());
        verify(taskRepository, never()).save(any());
        verify(eventBus, never()).emit(any());
    }

    // AC-2.2
    @Test
    void bulkUpdateToInProgressRejectsWholeRequest() {
        assertRejectedWholeRequest(bulk(new BulkStatusItem("T-1", TaskStatus.IN_PROGRESS)));
    }

    // AC-2.2
    @Test
    void bulkUpdateToTodoRejectsWholeRequest() {
        assertRejectedWholeRequest(bulk(new BulkStatusItem("T-1", TaskStatus.TODO)));
    }

    // AC-2.2
    @Test
    void bulkUpdateWithOneDisallowedTargetRejectsValidItemsToo() {
        assertRejectedWholeRequest(bulk(new BulkStatusItem("T-2", TaskStatus.DONE), new BulkStatusItem("T-1", TaskStatus.TODO)));
    }

    // AC-2.5
    @Test
    void bulkUpdateWithEmptyListRejectsWholeRequest() {
        assertRejectedWholeRequest(new BulkStatusRequest(List.of()));
    }

    // AC-2.5
    @Test
    void bulkUpdateWithNullListRejectsWholeRequest() {
        assertRejectedWholeRequest(new BulkStatusRequest(null));
    }

    private List<BulkStatusItem> distinctItems(int count) {
        List<BulkStatusItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add(new BulkStatusItem("T-" + i, TaskStatus.DONE));
        }
        return items;
    }

    // AC-2.5
    @Test
    void bulkUpdateWith51ItemsRejectsWholeRequest() {
        assertRejectedWholeRequest(new BulkStatusRequest(distinctItems(51)));
    }

    // AC-2.5
    @Test
    void bulkUpdateWithExactly50ItemsIsAccepted() {
        for (int i = 1; i <= 50; i++) {
            when(taskRepository.findById("T-" + i)).thenReturn(Optional.of(bulkTask("T-" + i, "m1", "u1", TaskStatus.TODO)));
        }
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        BulkStatusResponse response = service.updateStatusBulk(MANAGER, new BulkStatusRequest(distinctItems(50)));

        assertThat(response.updatedCount()).isEqualTo(50);
        assertThat(response.failedCount()).isZero();
        assertThat(response.results()).hasSize(50);
        verify(taskRepository, times(50)).save(any(Task.class));
        verify(eventBus, times(50)).emit(any(TaskStatusChangedEvent.class));
    }

    // AC-2.6
    @Test
    void bulkUpdateWithDuplicateTaskIdRejectsWholeRequest() {
        assertRejectedWholeRequest(bulk(new BulkStatusItem("T-1", TaskStatus.DONE), new BulkStatusItem("T-1", TaskStatus.BLOCKED)));
    }

    private static final AuthenticatedUser LEAD = new AuthenticatedUser("lead", "s1", StaffRole.DEPARTMENT_LEAD);
    private static final AuthenticatedUser ASSOC = new AuthenticatedUser("assoc", "s1", StaffRole.ASSOCIATE);
    private static final AuthenticatedUser REGIONAL = new AuthenticatedUser("reg", "region-1", StaffRole.REGIONAL_MANAGER);

    // AC-3.1
    @Test
    void bulkUpdateSavesOneAuditEntryPerUpdatedTask() {
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(bulkTask("T-1", "m1", "u1", TaskStatus.TODO)));
        when(taskRepository.findById("T-2")).thenReturn(Optional.of(bulkTask("T-2", "m1", "u1", TaskStatus.IN_PROGRESS)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        AuthenticatedUser assignee = new AuthenticatedUser("u1", "s1", StaffRole.ASSOCIATE);

        service.updateStatusBulk(assignee, bulk(new BulkStatusItem("T-1", TaskStatus.DONE, "Finished on shift"),
            new BulkStatusItem("T-2", TaskStatus.BLOCKED)));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository, times(2)).save(captor.capture());
        AuditEntry first = captor.getAllValues().get(0);
        assertThat(first.taskId()).isEqualTo("T-1");
        assertThat(first.actorId()).isEqualTo("u1");
        assertThat(first.oldStatus()).isEqualTo("TODO");
        assertThat(first.newStatus()).isEqualTo("DONE");
        assertThat(first.note()).isEqualTo("Finished on shift");
        assertThat(first.id()).isNotEmpty();
        assertThat(first.occurredAt()).isNotNull();
        AuditEntry second = captor.getAllValues().get(1);
        assertThat(second.taskId()).isEqualTo("T-2");
        assertThat(second.actorId()).isEqualTo("u1");
        assertThat(second.oldStatus()).isEqualTo("IN_PROGRESS");
        assertThat(second.newStatus()).isEqualTo("BLOCKED");
        assertThat(second.note()).isNull();
        assertThat(second.id()).isNotEmpty().isNotEqualTo(first.id());
        assertThat(second.occurredAt()).isNotNull();
    }

    // AC-3.2
    @Test
    void failedItemsAndRejectedRequestsSaveNoAuditEntry() {
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(bulkTask("T-1", "lead", "lead", TaskStatus.TODO)));
        when(taskRepository.findById("T-2")).thenReturn(Optional.of(bulkTask("T-2", "m1", "u1", TaskStatus.DONE)));
        when(auditRepository.findByTaskId(any())).thenReturn(List.of());

        BulkStatusResponse forbidden = service.updateStatusBulk(ASSOC, bulk(new BulkStatusItem("T-1", TaskStatus.DONE)));
        BulkStatusResponse conflict = service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-2", TaskStatus.BLOCKED)));
        assertThatThrownBy(() -> service.updateStatusBulk(MANAGER, new BulkStatusRequest(List.of())))
            .isInstanceOf(ValidationError.class);

        assertThat(forbidden.results().get(0).errorCode()).isEqualTo("FORBIDDEN");
        assertThat(conflict.results().get(0).errorCode()).isEqualTo("CONFLICT");
        verify(auditRepository, never()).save(any());
        assertThat(service.listAudit(MANAGER, "T-1")).isEmpty();
        assertThat(service.listAudit(MANAGER, "T-2")).isEmpty();
    }

    // AC-3.3
    @Test
    void listAuditReturnsRepositoryEntriesOldestFirst() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        AuditEntry a = new AuditEntry("a1", "T-1", "m1", "TODO", "BLOCKED", null, t);
        AuditEntry b = new AuditEntry("a2", "T-1", "m1", "BLOCKED", "DONE", null, t.plusSeconds(1));
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(bulkTask("T-1", "m1", "u1", TaskStatus.DONE)));
        when(auditRepository.findByTaskId("T-1")).thenReturn(List.of(a, b));

        List<AuditEntry> entries = service.listAudit(MANAGER, "T-1");

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).oldStatus()).isEqualTo("TODO");
        assertThat(entries.get(0).newStatus()).isEqualTo("BLOCKED");
        assertThat(entries.get(0).actorId()).isEqualTo("m1");
        assertThat(entries.get(1).oldStatus()).isEqualTo("BLOCKED");
        assertThat(entries.get(1).newStatus()).isEqualTo("DONE");
        assertThat(entries.get(1).actorId()).isEqualTo("m1");
    }

    // AC-3.3
    @Test
    void sequentialBulkUpdatesSaveEntriesInOrder() {
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(bulkTask("T-1", "m1", "u1", TaskStatus.TODO)),
            Optional.of(bulkTask("T-1", "m1", "u1", TaskStatus.BLOCKED)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-1", TaskStatus.BLOCKED)));
        service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-1", TaskStatus.DONE)));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).oldStatus()).isEqualTo("TODO");
        assertThat(captor.getAllValues().get(0).newStatus()).isEqualTo("BLOCKED");
        assertThat(captor.getAllValues().get(1).oldStatus()).isEqualTo("BLOCKED");
        assertThat(captor.getAllValues().get(1).newStatus()).isEqualTo("DONE");
        assertThat(captor.getAllValues().get(0).actorId()).isEqualTo("m1");
        assertThat(captor.getAllValues().get(1).actorId()).isEqualTo("m1");
    }

    // AC-3.4
    @Test
    void listAuditIsForbiddenForEveryRoleExceptStoreManager() {
        for (AuthenticatedUser caller : List.of(LEAD, ASSOC, REGIONAL)) {
            assertThatThrownBy(() -> service.listAudit(caller, "T-1"))
                .isInstanceOf(ForbiddenError.class)
                .satisfies(e -> assertThat(((ForbiddenError) e).getCode()).isEqualTo("FORBIDDEN"))
                .satisfies(e -> assertThat(e.getMessage()).isNotEmpty());
        }
        verify(auditRepository, never()).findByTaskId(any());
    }

    // AC-3.4 (check order: role before existence)
    @Test
    void listAuditByNonManagerForUnknownTaskIsForbiddenNotNotFound() {
        assertThatThrownBy(() -> service.listAudit(ASSOC, "T-404")).isInstanceOf(ForbiddenError.class);
        verify(taskRepository, never()).findById(any());
    }

    // AC-3.5
    @Test
    void listAuditOfUnknownTaskIsNotFoundForManager() {
        when(taskRepository.findById("T-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAudit(MANAGER, "T-404"))
            .isInstanceOf(NotFoundError.class)
            .hasMessage("Task not found: T-404");
        verify(auditRepository, never()).findByTaskId(any());
    }

    private static String note(int length) {
        return "n".repeat(length);
    }

    // AC-3.6
    @Test
    void bulkUpdateWithNoteOver500CharactersRejectsWholeRequest() {
        assertRejectedWholeRequest(bulk(new BulkStatusItem("T-1", TaskStatus.DONE, note(501))));
        verify(auditRepository, never()).save(any());
    }

    // AC-3.6
    @Test
    void bulkUpdateWithOneLongNoteRejectsValidItemsToo() {
        assertRejectedWholeRequest(bulk(new BulkStatusItem("T-2", TaskStatus.DONE, "ok"),
            new BulkStatusItem("T-1", TaskStatus.DONE, note(501))));
        verify(auditRepository, never()).save(any());
    }

    // AC-3.6
    @Test
    void bulkUpdateWithNoteOfExactly500CharactersIsAccepted() {
        when(taskRepository.findById("T-1")).thenReturn(Optional.of(bulkTask("T-1", "m1", "u1", TaskStatus.TODO)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        String boundary = note(500);

        BulkStatusResponse response = service.updateStatusBulk(MANAGER, bulk(new BulkStatusItem("T-1", TaskStatus.DONE, boundary)));

        assertThat(response.updatedCount()).isEqualTo(1);
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().note()).isEqualTo(boundary);
    }
}
