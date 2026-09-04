package com.das.cleanddd.domain.catalog.events;

public record ProductActivatedEvent(
        String id,
        Boolean active) implements ProductDomainEvent {
}
