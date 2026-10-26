package com.storeops.activities.model;

import java.time.Instant;

public record AuditEntry(String id, String taskId, String actorId, String oldStatus, String newStatus, String note,
                         Instant occurredAt) {
}
