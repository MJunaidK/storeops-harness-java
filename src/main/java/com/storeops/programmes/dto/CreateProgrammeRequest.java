package com.storeops.programmes.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProgrammeRequest(@NotBlank String name, String description) {
}
