package com.das.infra.service.catalog.events;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.catalog.reservation.usecases.ReserveStockForOrderInputDTO;
import com.das.cleanddd.domain.catalog.reservation.usecases.StockReservationOutputDTO;
import com.das.cleanddd.domain.catalog.reservation.usecases.StockReservationUseCaseFactory;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Applies an order event to the catalog's stock in one transaction: idempotent (eventId recorded in
 * {@code processed_event}; the use cases are idempotent per order as well). The stock change, the
 * reservation, its outbox answer and the processed-event row commit together.
 */
@Service
public class OrderEventHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);

    private final ProcessedEventJpaRepository processedEvents;
    private final StockReservationUseCaseFactory useCases;
    private final Clock clock;

    @Autowired
    public OrderEventHandler(ProcessedEventJpaRepository processedEvents, StockReservationUseCaseFactory useCases) {
        this(processedEvents, useCases, Clock.systemUTC());
    }

    OrderEventHandler(ProcessedEventJpaRepository processedEvents, StockReservationUseCaseFactory useCases, Clock clock) {
        this.processedEvents = processedEvents;
        this.useCases = useCases;
        this.clock = clock;
    }

    public enum Outcome { APPLIED, DUPLICATE }

    @Transactional
    public Outcome handle(OrderSagaEvent event) {
        String eventId = event.eventId().toString();
        if (processedEvents.existsById(eventId)) {
            log.debug("Duplicate order event skipped: eventId={}", eventId);
            return Outcome.DUPLICATE;
        }
        // A concurrent duplicate fails on this primary key at commit; the retry then sees it as processed.
        processedEvents.save(new ProcessedEventEntity(eventId, event.eventType(), Instant.now(clock)));

        StockReservationOutputDTO reservation;
        try {
            reservation = switch (event) {
                case OrderSagaEvent.Created c -> useCases.getReserveStockForOrderUseCase().execute(
                        new ReserveStockForOrderInputDTO(c.orderId(), c.lines().stream()
                                .map(l -> new ReserveStockForOrderInputDTO.Line(l.productId(), l.quantity()))
                                .toList()));
                case OrderSagaEvent.Rejected r -> useCases.getReleaseStockForOrderUseCase().execute(r.orderId());
                case OrderSagaEvent.Delivered d -> useCases.getFulfilStockReservationUseCase().execute(d.orderId());
            };
        } catch (DomainException e) {
            throw new IllegalStateException("Cannot apply " + event.eventType() + " to order " + event.orderId()
                    + ": " + e.getMessage(), e);
        }
        log.info("Order event applied: eventId={} type={} orderId={} reservation={}",
                eventId, event.eventType(), event.orderId(), reservation.status());
        return Outcome.APPLIED;
    }
}
