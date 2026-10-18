package com.storeops.activities.service;

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
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

    private final TaskRepository taskRepository;
    private final StaffService staffService;
    private final EventBus eventBus;

    public ActivityService(TaskRepository taskRepository, StaffService staffService, EventBus eventBus) {
        this.taskRepository = taskRepository;
        this.staffService = staffService;
        this.eventBus = eventBus;
    }

    public List<Task> list(String programmeId, TaskStatus status) {
        return taskRepository.findAll(programmeId, status);
    }

    public Task get(String id) {
        return taskRepository.findById(id).orElseThrow(() -> new NotFoundError("Task not found: " + id));
    }

    public Task create(AuthenticatedUser caller, CreateTaskRequest request) {
        if (request.assigneeId() != null) {
            staffService.requireById(request.assigneeId());
        }
        Instant now = Instant.now();
        Task task = new Task(UUID.randomUUID().toString(), request.programmeId(), request.title(), request.description(),
            TaskStatus.TODO,
            request.priority() == null ? TaskPriority.MEDIUM : request.priority(),
            request.category() == null ? TaskCategory.GENERAL : request.category(),
            request.assigneeId(), caller.id(), request.dueDate(), now, now);
        Task saved = taskRepository.save(task);
        eventBus.emit(new TaskCreatedEvent(saved.id(), saved.title(), saved.ownerId(), saved.assigneeId(), now));
        return saved;
    }

    public Task update(String id, UpdateTaskRequest request) {
        Task existing = get(id);
        if (request.assigneeId() != null) {
            staffService.requireById(request.assigneeId());
        }
        Instant now = Instant.now();
        Task updated = taskRepository.save(
            existing.withChanges(request.status(), request.priority(), request.category(), request.assigneeId(), now));
        if (updated.status() != existing.status()) {
            eventBus.emit(new TaskStatusChangedEvent(updated.id(), updated.title(), updated.ownerId(), updated.assigneeId(),
                existing.status().name(), updated.status().name(), now));
        }
        return updated;
    }

    public void delete(AuthenticatedUser caller, String id) {
        Task task = get(id);
        boolean isOwner = task.ownerId().equals(caller.id());
        if (!isOwner && caller.role() != StaffRole.STORE_MANAGER) {
            throw new ForbiddenError("Only the task owner or a store manager can delete a task");
        }
        taskRepository.deleteById(task.id());
    }
}
