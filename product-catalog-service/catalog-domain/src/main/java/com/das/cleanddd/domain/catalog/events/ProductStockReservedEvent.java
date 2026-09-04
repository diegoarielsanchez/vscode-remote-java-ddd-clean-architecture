package com.das.cleanddd.domain.catalog.events;

public record ProductStockReservedEvent(
        String id,
        Integer quantityReserved,
        Integer remainingStock) implements ProductDomainEvent {
}
