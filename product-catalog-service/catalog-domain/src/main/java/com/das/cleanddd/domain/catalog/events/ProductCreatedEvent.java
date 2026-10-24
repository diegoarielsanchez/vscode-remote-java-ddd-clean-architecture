package com.das.cleanddd.domain.catalog.events;

import java.math.BigDecimal;

public record ProductCreatedEvent(
        String id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Boolean active,
        String gtin,
        Boolean lotTracked) implements ProductDomainEvent {

    /** Without a GTIN, lot-tracked. */
    public ProductCreatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active) {
        this(id, name, description, price, unit, active, null, true);
    }

    /** Lot-tracked. */
    public ProductCreatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active,
                               String gtin) {
        this(id, name, description, price, unit, active, gtin, true);
    }
}
