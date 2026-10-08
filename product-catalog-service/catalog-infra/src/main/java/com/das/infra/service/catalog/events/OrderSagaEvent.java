package com.das.infra.service.catalog.events;

import java.util.List;
import java.util.UUID;

/** A validated {@code order.*} event that moves the catalog's side of the stock saga. */
public sealed interface OrderSagaEvent {

    UUID eventId();

    String eventType();

    String orderId();

    /** {@code order.created}: reserve these lines. */
    record Created(UUID eventId, String eventType, String orderId, List<Line> lines) implements OrderSagaEvent {
        public Created {
            lines = List.copyOf(lines);
        }
    }

    /** {@code order.rejected}: give the order's stock back. */
    record Rejected(UUID eventId, String eventType, String orderId) implements OrderSagaEvent {
    }

    /** {@code order.delivered}: the reservation is fulfilled. */
    record Delivered(UUID eventId, String eventType, String orderId) implements OrderSagaEvent {
    }

    record Line(String productId, int quantity) {
    }
}
