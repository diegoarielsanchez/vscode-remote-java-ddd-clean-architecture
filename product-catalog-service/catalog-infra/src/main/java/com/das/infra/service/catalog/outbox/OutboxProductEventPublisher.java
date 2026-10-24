package com.das.infra.service.catalog.outbox;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.shared.audit.ChangeTracked;
import com.das.cleanddd.domain.shared.bus.event.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * {@link IProductEventPublisher} adapter implementing the transactional outbox: instead of talking to
 * the broker, it appends the integration event to {@code outbox_event} inside the caller's
 * transaction ({@code MANDATORY}: calling it outside a unit of work is a programming error).
 * {@link ProductOutboxRelay} publishes the rows afterwards.
 */
@Primary
@Service
@Profile("!dev")
public class OutboxProductEventPublisher implements IProductEventPublisher {

    public static final String PRODUCER = "product-catalog-service";

    /** Fixed serialization for the wire contract, independent of the application's ObjectMapper. */
    static final ObjectMapper CONTRACT_JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final OutboxEventJpaRepository outbox;
    private final AggregateVersionJpaRepository versions;
    private final AuditEventJpaRepository audit;
    private final EventContext context;
    private final Clock clock;

    @Autowired
    public OutboxProductEventPublisher(OutboxEventJpaRepository outbox, AggregateVersionJpaRepository versions,
                                   AuditEventJpaRepository audit, ObjectProvider<EventContext> context) {
        this(outbox, versions, audit, context.getIfAvailable(() -> EventContext.NONE), Clock.systemUTC());
    }

    OutboxProductEventPublisher(OutboxEventJpaRepository outbox, AggregateVersionJpaRepository versions,
                            AuditEventJpaRepository audit, EventContext context, Clock clock) {
        this.outbox = outbox;
        this.versions = versions;
        this.audit = audit;
        this.context = context;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ProductDomainEvent event) {
        ProductIntegrationEvents.Mapped mapped = ProductIntegrationEvents.map(event);
        long version = nextVersion(mapped.aggregateId());

        EventEnvelope<ProductIntegrationEvents.ProductEventData> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                mapped.eventType(),
                ProductIntegrationEvents.SCHEMA_VERSION,
                mapped.aggregateId(),
                version,
                clock.instant(), // same transaction as the change: this is when it happened
                PRODUCER,
                context.correlationId(),
                context.actor(),
                mapped.data());

        String payload = toJson(envelope);
        outbox.save(OutboxEventEntity.pending(envelope.eventId(), ProductIntegrationEvents.AGGREGATE_TYPE,
                envelope.aggregateId(), version, envelope.eventType(), payload, envelope.occurredAt()));
        // Kept permanently, unlike the outbox row: the audit trail (ALCOA+).
        audit.save(AuditEventEntity.of(ProductIntegrationEvents.AGGREGATE_TYPE, envelope, payload, changesOf(event),
                event instanceof ChangeTracked tracked ? tracked.reason() : null));
    }

    private long nextVersion(String aggregateId) {
        String key = ProductIntegrationEvents.AGGREGATE_TYPE + ":" + aggregateId;
        AggregateVersionEntity row = versions.findForUpdate(key)
                .orElseGet(() -> versions.save(new AggregateVersionEntity(key)));
        return row.next();
    }

    /** The edit's field changes with their previous values, for the audit trail only; {@code null} if not an edit. */
    private static String changesOf(Object event) {
        if (!(event instanceof ChangeTracked tracked)) {
            return null;
        }
        try {
            return CONTRACT_JSON.writeValueAsString(tracked.changes());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the changes of " + event.getClass().getSimpleName(), e);
        }
    }

    private static String toJson(EventEnvelope<?> envelope) {
        try {
            return CONTRACT_JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize integration event " + envelope.eventType(), e);
        }
    }
}
