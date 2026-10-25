package com.storeops.activities.dto;

import java.util.List;

public record BulkStatusRequest(List<BulkStatusItem> updates) {

    public BulkStatusRequest {
        updates = updates == null ? List.of() : List.copyOf(updates);
    }
}
