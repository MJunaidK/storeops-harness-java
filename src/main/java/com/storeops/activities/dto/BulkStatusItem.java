package com.storeops.activities.dto;

import com.storeops.activities.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BulkStatusItem(@NotBlank String taskId, @NotNull TaskStatus status) {
}
