package com.storeops.shared.event;

import java.time.Instant;

public record TaskStatusChangedEvent(String taskId, String title, String ownerId, String assigneeId,
                                     String oldStatus, String newStatus, Instant occurredAt) implements DomainEvent {
}
