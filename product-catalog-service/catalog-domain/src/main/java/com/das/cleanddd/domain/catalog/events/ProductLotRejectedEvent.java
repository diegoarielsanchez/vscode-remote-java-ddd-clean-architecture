package com.das.cleanddd.domain.catalog.events;

/**
 * Quality rejected a quarantined lot: its {@code quantity} units leave the stock on hand for good and await
 * destruction. {@code remainingStock} is the product's stock on hand afterwards.
 */
public record ProductLotRejectedEvent(String id, String batchNumber, int quantity, int remainingStock, String reason)
        implements ProductDomainEvent {
}
