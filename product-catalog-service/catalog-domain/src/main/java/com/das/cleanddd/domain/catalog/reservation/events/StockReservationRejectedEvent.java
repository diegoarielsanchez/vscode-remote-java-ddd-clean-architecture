package com.das.cleanddd.domain.catalog.reservation.events;

/** Not every line could be reserved; nothing is held for the order. */
public record StockReservationRejectedEvent(String orderId, String reason) implements StockReservationDomainEvent {
}
