package com.das.infra.service.catalog.outbox;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.catalog.reservation.events.StockReservationConfirmedEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationDomainEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationRejectedEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationReleasedEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Maps the stock saga's domain events to the public {@code catalog.events} contract (schema
 * version 1). Routing key = event type. The aggregate is the reservation, whose id is the order id.
 */
public final class StockReservationIntegrationEvents {

    public static final String AGGREGATE_TYPE = "reservation";
    public static final int SCHEMA_VERSION = 1;

    /** Event payload ({@code data}); null fields do not apply to the event type and are left out. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReservationEventData(String orderId, List<Line> lines, String reason) {}

    public record Line(String productId, int quantity, String productName, BigDecimal unitPrice) {}

    public record Mapped(String eventType, String aggregateId, ReservationEventData data) {}

    private StockReservationIntegrationEvents() {}

    public static Mapped map(StockReservationDomainEvent event) {
        return switch (event) {
            case StockReservationConfirmedEvent e -> new Mapped("catalog.reservation.confirmed", e.orderId(),
                    new ReservationEventData(e.orderId(), e.lines().stream()
                            .map(l -> new Line(l.productId(), l.quantity(), l.productName(), l.unitPrice()))
                            .toList(), null));
            case StockReservationRejectedEvent e -> new Mapped("catalog.reservation.rejected", e.orderId(),
                    new ReservationEventData(e.orderId(), null, e.reason()));
            case StockReservationReleasedEvent e -> new Mapped("catalog.reservation.released", e.orderId(),
                    new ReservationEventData(e.orderId(), null, null));
        };
    }
}
