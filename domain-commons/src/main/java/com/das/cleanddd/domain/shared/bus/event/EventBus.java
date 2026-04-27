package com.das.cleanddd.domain.shared.bus.event;

import java.util.List;

public interface EventBus {
    void publish(final List<DomainEvent> events);
}
