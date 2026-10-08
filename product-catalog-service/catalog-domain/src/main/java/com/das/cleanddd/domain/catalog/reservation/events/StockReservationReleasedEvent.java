package com.das.cleanddd.domain.catalog.reservation.events;

/** The order was rejected and its reserved stock went back to the pool. */
public record StockReservationReleasedEvent(String orderId) implements StockReservationDomainEvent {
}
