package com.das.cleanddd.domain.catalog.events;

public record ProductRestockedEvent(
        String id,
        Integer quantityAdded,
        Integer remainingStock) implements ProductDomainEvent {
}
