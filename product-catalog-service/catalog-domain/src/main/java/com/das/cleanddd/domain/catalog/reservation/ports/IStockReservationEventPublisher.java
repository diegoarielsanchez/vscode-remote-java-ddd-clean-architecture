package com.das.cleanddd.domain.catalog.reservation.ports;

import com.das.cleanddd.domain.catalog.reservation.events.StockReservationDomainEvent;

public interface IStockReservationEventPublisher {

    void publish(StockReservationDomainEvent event);
}
