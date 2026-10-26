package com.storeops.activities.dto;

import com.storeops.activities.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BulkStatusItem(@NotBlank String taskId, @NotNull TaskStatus status, @Size(max = 500) String note) {

    public BulkStatusItem(String taskId, TaskStatus status) {
        this(taskId, status, null);
    }
}
