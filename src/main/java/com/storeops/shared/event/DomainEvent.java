package com.storeops.shared.event;

import java.time.Instant;

public interface DomainEvent {

    Instant occurredAt();
}
