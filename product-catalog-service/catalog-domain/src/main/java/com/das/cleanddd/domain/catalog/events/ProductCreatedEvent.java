package com.das.cleanddd.domain.catalog.events;

import java.math.BigDecimal;

public record ProductCreatedEvent(
        String id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Boolean active,
        String gtin) implements ProductDomainEvent {

    /** Without a GTIN. */
    public ProductCreatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active) {
        this(id, name, description, price, unit, active, null);
    }
}
