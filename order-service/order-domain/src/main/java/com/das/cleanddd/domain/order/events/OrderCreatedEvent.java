package com.das.cleanddd.domain.order.events;

import java.util.List;

/**
 * Starts the stock saga: product-catalog-service reserves every line (all or nothing) and answers
 * with a confirmed or rejected reservation. Lines carry no price — the catalog sets it.
 */
public record OrderCreatedEvent(
        String id,
        String medicalSalesRepId,
        List<Line> lines) implements OrderDomainEvent {

    public OrderCreatedEvent {
        lines = List.copyOf(lines);
    }

    public record Line(String productId, int quantity) {
    }
}
