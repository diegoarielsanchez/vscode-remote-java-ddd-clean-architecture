package com.das.cleanddd.domain.catalog.events;

import java.math.BigDecimal;

public record ProductCreatedEvent(
        String id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Boolean active) implements ProductDomainEvent {
}
