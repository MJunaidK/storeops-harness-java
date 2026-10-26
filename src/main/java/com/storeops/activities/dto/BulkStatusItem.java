package com.storeops.activities.dto;

import com.storeops.activities.model.TaskStatus;

public record BulkStatusItem(String taskId, TaskStatus status) {
}
