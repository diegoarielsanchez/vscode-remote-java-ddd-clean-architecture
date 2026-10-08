package com.das.cleanddd.domain.catalog.reservation.events;

public sealed interface StockReservationDomainEvent
        permits StockReservationConfirmedEvent, StockReservationRejectedEvent, StockReservationReleasedEvent {

    String orderId();
}
