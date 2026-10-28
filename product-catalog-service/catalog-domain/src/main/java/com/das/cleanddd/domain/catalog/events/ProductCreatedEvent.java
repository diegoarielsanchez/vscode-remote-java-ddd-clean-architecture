package com.das.cleanddd.domain.catalog.events;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.storage.StorageCondition;

public record ProductCreatedEvent(
        String id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Boolean active,
        String gtin,
        Boolean lotTracked,
        StorageCondition storageCondition) implements ProductDomainEvent {

    /** Without a GTIN, lot-tracked. */
    public ProductCreatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active) {
        this(id, name, description, price, unit, active, null, true, null);
    }

    /** Lot-tracked. */
    public ProductCreatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active,
                               String gtin) {
        this(id, name, description, price, unit, active, gtin, true, null);
    }

    /** Without storage conditions. */
    public ProductCreatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active,
                               String gtin, Boolean lotTracked) {
        this(id, name, description, price, unit, active, gtin, lotTracked, null);
    }
}
