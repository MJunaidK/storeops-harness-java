package com.storeops.shared.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class EventBus {

    private final ApplicationEventPublisher publisher;

    public EventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void emit(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
