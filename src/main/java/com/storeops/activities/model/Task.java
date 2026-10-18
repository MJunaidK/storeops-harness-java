package com.storeops.activities.model;

import java.time.Instant;
import java.time.LocalDate;

public record Task(String id, String programmeId, String title, String description, TaskStatus status, TaskPriority priority,
                   TaskCategory category, String assigneeId, String ownerId, LocalDate dueDate, Instant createdAt,
                   Instant updatedAt) {

    public Task withChanges(TaskStatus newStatus, TaskPriority newPriority, TaskCategory newCategory, String newAssigneeId,
                            Instant now) {
        return new Task(id, programmeId, title, description,
            newStatus == null ? status : newStatus,
            newPriority == null ? priority : newPriority,
            newCategory == null ? category : newCategory,
            newAssigneeId == null ? assigneeId : newAssigneeId,
            ownerId, dueDate, createdAt, now);
    }
}
