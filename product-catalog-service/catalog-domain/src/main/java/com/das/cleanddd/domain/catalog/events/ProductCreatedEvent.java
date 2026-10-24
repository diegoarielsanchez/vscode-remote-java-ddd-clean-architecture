package com.das.cleanddd.domain.catalog.events;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductCreatedEvent(
        String id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Boolean active,
        String batchNumber,
        String serialNumber,
        LocalDate expirationDate) implements ProductDomainEvent {

    /** Without batch number, serial number or expiration date. */
    public ProductCreatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active) {
        this(id, name, description, price, unit, active, null, null, null);
    }
}
