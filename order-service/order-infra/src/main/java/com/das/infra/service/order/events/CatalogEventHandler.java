package com.das.infra.service.order.events;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.order.usecases.dtos.ConfirmOrderStockInputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.OrderOutputDTO;
import com.das.cleanddd.domain.order.usecases.dtos.RejectOrderForStockInputDTO;
import com.das.cleanddd.domain.order.usecases.services.OrderUseCaseFactory;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Applies the catalog's answer to the order in one transaction: idempotent (eventId recorded in
 * {@code processed_event}), and the use cases themselves ignore an order that has already left
 * AWAITING_STOCK. The order change, its outbox rows and the processed-event row commit together.
 */
@Service
public class CatalogEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogEventHandler.class);

    private final ProcessedEventJpaRepository processedEvents;
    private final OrderUseCaseFactory useCases;
    private final Clock clock;

    @Autowired
    public CatalogEventHandler(ProcessedEventJpaRepository processedEvents, OrderUseCaseFactory useCases) {
        this(processedEvents, useCases, Clock.systemUTC());
    }

    CatalogEventHandler(ProcessedEventJpaRepository processedEvents, OrderUseCaseFactory useCases, Clock clock) {
        this.processedEvents = processedEvents;
        this.useCases = useCases;
        this.clock = clock;
    }

    public enum Outcome { APPLIED, DUPLICATE }

    @Transactional
    public Outcome handle(StockReservationReply reply) {
        String eventId = reply.eventId().toString();
        if (processedEvents.existsById(eventId)) {
            log.debug("Duplicate catalog event skipped: eventId={}", eventId);
            return Outcome.DUPLICATE;
        }
        // A concurrent duplicate fails on this primary key at commit; the retry then sees it as processed.
        processedEvents.save(new ProcessedEventEntity(eventId, reply.eventType(), Instant.now(clock)));

        OrderOutputDTO order;
        try {
            order = switch (reply) {
                case StockReservationReply.Confirmed c -> useCases.getConfirmOrderStockUseCase().execute(
                        new ConfirmOrderStockInputDTO(c.orderId(), c.lines().stream()
                                .map(l -> new ConfirmOrderStockInputDTO.ReservedLine(l.productId(), l.productName(), l.unitPrice()))
                                .toList()));
                case StockReservationReply.Rejected r -> useCases.getRejectOrderForStockUseCase().execute(
                        new RejectOrderForStockInputDTO(r.orderId(), r.reason()));
            };
        } catch (DomainException e) {
            // Unknown order or a reservation that does not match it: retried, then dead-lettered for a human.
            throw new IllegalStateException("Cannot apply " + reply.eventType() + " to order " + reply.orderId()
                    + ": " + e.getMessage(), e);
        }
        log.info("Catalog event applied: eventId={} type={} orderId={} status={}",
                eventId, reply.eventType(), reply.orderId(), order.status());
        return Outcome.APPLIED;
    }
}
