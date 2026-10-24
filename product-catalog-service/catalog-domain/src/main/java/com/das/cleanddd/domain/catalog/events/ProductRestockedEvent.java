package com.das.cleanddd.domain.catalog.events;

import java.time.LocalDate;

/** Units of one batch received into stock. */
public record ProductRestockedEvent(
        String id,
        Integer quantityAdded,
        Integer remainingStock,
        String batchNumber,
        LocalDate expirationDate) implements ProductDomainEvent {

    /** Without the lot. */
    public ProductRestockedEvent(String id, Integer quantityAdded, Integer remainingStock) {
        this(id, quantityAdded, remainingStock, null, null);
    }
}
