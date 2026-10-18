package com.storeops.programmes.dto;

import com.storeops.programmes.model.ProjectRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(@NotBlank String userId, @NotNull ProjectRole role) {
}
