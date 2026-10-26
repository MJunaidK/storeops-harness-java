package com.storeops.activities.service;

import com.storeops.activities.dto.BulkStatusItem;
import com.storeops.activities.dto.BulkStatusRequest;
import com.storeops.activities.dto.BulkStatusResponse;
import com.storeops.activities.dto.BulkStatusResult;
import com.storeops.activities.dto.CreateTaskRequest;
import com.storeops.activities.dto.UpdateTaskRequest;
import com.storeops.activities.model.Task;
import com.storeops.activities.model.TaskCategory;
import com.storeops.activities.model.TaskPriority;
import com.storeops.activities.model.TaskStatus;
import com.storeops.activities.repository.TaskRepository;
import com.storeops.shared.error.AppError;
import com.storeops.shared.error.ConflictError;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

    private static final int MAX_BULK_ITEMS = 50;

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

    public BulkStatusResponse updateStatusBulk(AuthenticatedUser caller, BulkStatusRequest request) {
        validateBulkRequest(request);
        List<BulkStatusResult> results = new ArrayList<>();
        int updated = 0;
        for (BulkStatusItem item : request.updates()) {
            BulkStatusResult result = updateOneStatus(caller, item);
            if (result.success()) {
                updated++;
            }
            results.add(result);
        }
        return new BulkStatusResponse(updated, results.size() - updated, results);
    }

    private void validateBulkRequest(BulkStatusRequest request) {
        List<BulkStatusItem> updates = request == null ? null : request.updates();
        if (updates == null || updates.isEmpty()) {
            throw new ValidationError("updates must contain between 1 and " + MAX_BULK_ITEMS + " items");
        }
        if (updates.size() > MAX_BULK_ITEMS) {
            throw new ValidationError("updates must contain at most " + MAX_BULK_ITEMS + " items");
        }
        Set<String> seen = new HashSet<>();
        for (BulkStatusItem item : updates) {
            if (item == null || item.taskId() == null || item.taskId().isBlank() || item.status() == null) {
                throw new ValidationError("Every update needs a taskId and a status");
            }
            if (!seen.add(item.taskId())) {
                throw new ValidationError("Duplicate taskId in updates: " + item.taskId());
            }
            if (item.status() != TaskStatus.DONE && item.status() != TaskStatus.BLOCKED) {
                throw new ValidationError("Target status must be DONE or BLOCKED, got " + item.status());
            }
        }
    }

    private BulkStatusResult updateOneStatus(AuthenticatedUser caller, BulkStatusItem item) {
        Task existing = null;
        try {
            existing = get(item.taskId());
            boolean permitted = caller.role() == StaffRole.STORE_MANAGER
                || caller.id().equals(existing.assigneeId())
                || caller.id().equals(existing.ownerId());
            if (!permitted) {
                throw new ForbiddenError("Only the assignee, the owner or a store manager can change the status of task "
                    + existing.id());
            }
            if (!isAllowedTransition(existing.status(), item.status())) {
                throw new ConflictError("Cannot change task " + existing.id() + " from " + existing.status() + " to "
                    + item.status());
            }
            Instant now = Instant.now();
            Task updated = taskRepository.save(existing.withChanges(item.status(), null, null, null, now));
            eventBus.emit(new TaskStatusChangedEvent(updated.id(), updated.title(), updated.ownerId(), updated.assigneeId(),
                existing.status().name(), updated.status().name(), now));
            return new BulkStatusResult(item.taskId(), true, updated.status().name(), null, null);
        } catch (AppError error) {
            String current = existing == null ? null : existing.status().name();
            return new BulkStatusResult(item.taskId(), false, current, error.getCode(), error.getMessage());
        }
    }

    private boolean isAllowedTransition(TaskStatus from, TaskStatus to) {
        if (to != TaskStatus.DONE && to != TaskStatus.BLOCKED) {
            return false;
        }
        return from == TaskStatus.TODO || from == TaskStatus.IN_PROGRESS || (from == TaskStatus.BLOCKED && to == TaskStatus.DONE);
    }
}
