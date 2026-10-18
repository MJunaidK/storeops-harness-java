package com.storeops.activities.dto;

import com.storeops.activities.model.TaskCategory;
import com.storeops.activities.model.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateTaskRequest(@NotBlank String programmeId, @NotBlank String title, String description,
                                TaskPriority priority, TaskCategory category, String assigneeId, LocalDate dueDate) {
}
