package com.das.cleanddd.domain.catalog.reservation.events;

import java.util.List;

import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;

/** Every line of the order is reserved; the lines carry the name and price snapshot. */
public record StockReservationConfirmedEvent(String orderId, List<ReservationLine> lines)
        implements StockReservationDomainEvent {

    public StockReservationConfirmedEvent {
        lines = List.copyOf(lines);
    }
}
