package com.das.cleanddd.domain.order.events;

/** product-catalog-service could not reserve every line; nothing stays reserved. */
public record OrderStockRejectedEvent(
        String id,
        String reason) implements OrderDomainEvent {
}
