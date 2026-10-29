package com.das.infra.service.catalog.outbox;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.catalog.reservation.events.StockReservationDomainEvent;
import com.das.cleanddd.domain.catalog.reservation.ports.IStockReservationEventPublisher;
import com.das.cleanddd.domain.shared.bus.event.EventEnvelope;
import tools.jackson.core.JacksonException;

/**
 * {@link IStockReservationEventPublisher} adapter: appends the saga's answers to the same
 * {@code outbox_event} table as product events, inside the caller's transaction ({@code MANDATORY}),
 * so the stock change and its answer commit together. {@link ProductOutboxRelay} publishes them.
 */
@Service
@Profile("!dev")
public class OutboxStockReservationEventPublisher implements IStockReservationEventPublisher {

    private final OutboxEventJpaRepository outbox;
    private final AggregateVersionJpaRepository versions;
    private final AuditEventJpaRepository audit;
    private final EventContext context;
    private final Clock clock;

    @Autowired
    public OutboxStockReservationEventPublisher(OutboxEventJpaRepository outbox, AggregateVersionJpaRepository versions,
                                                AuditEventJpaRepository audit, ObjectProvider<EventContext> context) {
        this(outbox, versions, audit, context.getIfAvailable(() -> EventContext.NONE), Clock.systemUTC());
    }

    OutboxStockReservationEventPublisher(OutboxEventJpaRepository outbox, AggregateVersionJpaRepository versions,
                                         AuditEventJpaRepository audit, EventContext context, Clock clock) {
        this.outbox = outbox;
        this.versions = versions;
        this.audit = audit;
        this.context = context;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(StockReservationDomainEvent event) {
        StockReservationIntegrationEvents.Mapped mapped = StockReservationIntegrationEvents.map(event);
        long version = nextVersion(mapped.aggregateId());

        EventEnvelope<StockReservationIntegrationEvents.ReservationEventData> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                mapped.eventType(),
                StockReservationIntegrationEvents.SCHEMA_VERSION,
                mapped.aggregateId(),
                version,
                clock.instant(),
                OutboxProductEventPublisher.PRODUCER,
                context.correlationId(),
                context.actor(),
                mapped.data());

        String payload = toJson(envelope);
        outbox.save(OutboxEventEntity.pending(envelope.eventId(), StockReservationIntegrationEvents.AGGREGATE_TYPE,
                envelope.aggregateId(), version, envelope.eventType(), payload, envelope.occurredAt()));
        // Kept permanently, unlike the outbox row: the audit trail (ALCOA+).
        audit.save(AuditEventEntity.of(StockReservationIntegrationEvents.AGGREGATE_TYPE, envelope, payload));
    }

    private long nextVersion(String aggregateId) {
        String key = StockReservationIntegrationEvents.AGGREGATE_TYPE + ":" + aggregateId;
        AggregateVersionEntity row = versions.findForUpdate(key)
                .orElseGet(() -> versions.save(new AggregateVersionEntity(key)));
        return row.next();
    }

    private static String toJson(EventEnvelope<?> envelope) {
        try {
            return OutboxProductEventPublisher.CONTRACT_JSON.writeValueAsString(envelope);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize integration event " + envelope.eventType(), e);
        }
    }
}
