package com.das.infra.service.order.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A validated {@code catalog.reservation.*} answer to an order's {@code order.created}. */
public sealed interface StockReservationReply {

    UUID eventId();

    String eventType();

    String orderId();

    /** Every line was reserved; one price per product. */
    record Confirmed(UUID eventId, String eventType, String orderId, List<PricedLine> lines)
            implements StockReservationReply {
        public Confirmed {
            lines = List.copyOf(lines);
        }
    }

    /** Not every line could be reserved; the catalog holds nothing for this order. */
    record Rejected(UUID eventId, String eventType, String orderId, String reason) implements StockReservationReply {
    }

    record PricedLine(String productId, String productName, BigDecimal unitPrice) {
    }
}
