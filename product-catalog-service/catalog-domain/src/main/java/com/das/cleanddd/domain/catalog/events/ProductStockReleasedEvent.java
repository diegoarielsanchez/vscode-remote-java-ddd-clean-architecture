package com.das.cleanddd.domain.catalog.events;

public record ProductStockReleasedEvent(
        String id,
        Integer quantityReleased,
        Integer remainingStock) implements ProductDomainEvent {
}
