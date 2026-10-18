package com.storeops.activities.dto;

import com.storeops.activities.model.TaskCategory;
import com.storeops.activities.model.TaskPriority;
import com.storeops.activities.model.TaskStatus;

public record UpdateTaskRequest(TaskStatus status, TaskPriority priority, TaskCategory category, String assigneeId) {
}
