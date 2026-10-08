package com.das.catalog.application.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.reservation.events.StockReservationDomainEvent;
import com.das.cleanddd.domain.catalog.reservation.ports.IStockReservationEventPublisher;

/**
 * No-op stock-saga publisher for the dev profile (no RabbitMQ broker required; the saga does not
 * run in dev, so orders stay AWAITING_STOCK there).
 */
@Profile("dev")
@Service
public class NoOpStockReservationEventPublisher implements IStockReservationEventPublisher {

    @Override
    public void publish(StockReservationDomainEvent event) {
        // intentionally empty — events are discarded in dev mode
    }
}
