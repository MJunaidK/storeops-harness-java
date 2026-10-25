package com.storeops.activities.dto;

import java.util.List;

public record BulkStatusResponse(int updatedCount, int failedCount, List<BulkStatusResult> results) {

    public BulkStatusResponse {
        results = List.copyOf(results);
    }
}
