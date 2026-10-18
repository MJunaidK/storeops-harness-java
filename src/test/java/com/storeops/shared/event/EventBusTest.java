package com.storeops.shared.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class EventBusTest {

    @Test
    void emitPublishesEventThroughSpringPublisher() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        TaskCreatedEvent event = new TaskCreatedEvent("t1", "Title", "o", "a", Instant.now());

        new EventBus(publisher).emit(event);

        verify(publisher).publishEvent(event);
    }
}
