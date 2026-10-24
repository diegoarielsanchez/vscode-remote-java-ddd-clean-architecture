package com.das.cleanddd.domain.catalog.events;

import java.math.BigDecimal;

public record ProductUpdatedEvent(
        String id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Boolean active,
        String gtin) implements ProductDomainEvent {

    /** Without a GTIN. */
    public ProductUpdatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active) {
        this(id, name, description, price, unit, active, null);
    }
}
