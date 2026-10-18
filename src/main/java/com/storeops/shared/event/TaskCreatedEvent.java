package com.storeops.shared.event;

import java.time.Instant;

public record TaskCreatedEvent(String taskId, String title, String ownerId, String assigneeId, Instant occurredAt)
    implements DomainEvent {
}
