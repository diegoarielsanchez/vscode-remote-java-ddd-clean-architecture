package com.das.cleanddd.domain.catalog.events;

public record ProductDeactivatedEvent(
        String id,
        Boolean active) implements ProductDomainEvent {
}
