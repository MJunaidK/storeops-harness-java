package com.storeops.activities.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.storeops.activities.dto.CreateTaskRequest;
import com.storeops.activities.dto.UpdateTaskRequest;
import com.storeops.activities.model.Task;
import com.storeops.activities.model.TaskCategory;
import com.storeops.activities.model.TaskPriority;
import com.storeops.activities.model.TaskStatus;
import com.storeops.activities.repository.TaskRepository;
import com.storeops.shared.error.ForbiddenError;
import com.storeops.shared.error.NotFoundError;
import com.storeops.shared.event.EventBus;
import com.storeops.shared.event.TaskCreatedEvent;
import com.storeops.shared.event.TaskStatusChangedEvent;
import com.storeops.shared.security.AuthenticatedUser;
import com.storeops.staff.model.StaffRole;
import com.storeops.staff.service.StaffService;
import java.time.Instant;
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
}
