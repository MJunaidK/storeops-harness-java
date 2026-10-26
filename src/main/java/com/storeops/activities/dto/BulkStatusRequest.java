package com.storeops.activities.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkStatusRequest(@NotEmpty @Valid List<BulkStatusItem> updates) {

    public BulkStatusRequest {
        updates = updates == null ? null : List.copyOf(updates);
    }
}
