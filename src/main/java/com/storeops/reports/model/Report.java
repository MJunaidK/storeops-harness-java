package com.storeops.reports.model;

import java.time.Instant;
import java.util.Map;

public record Report(String id, ReportType type, ReportStatus status, String storeId, Map<String, Long> metrics,
                     Instant generatedAt) {

    public Report {
        metrics = Map.copyOf(metrics);
    }
}
