package com.das.infra.service.healthcareprof.outbox;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.healthcareprof.events.HcpDomainEvent;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.shared.bus.event.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * {@link IHcpEventPublisher} adapter implementing the transactional outbox: instead of talking to
 * the broker, it appends the integration event to {@code outbox_event} inside the caller's
 * transaction ({@code MANDATORY}: calling it outside a unit of work is a programming error).
 * {@link HcpOutboxRelay} publishes the rows afterwards.
 */
@Primary
@Service
@Profile("!dev")
public class OutboxHcpEventPublisher implements IHcpEventPublisher {

    public static final String PRODUCER = "healthcare-prof-service";

    /** Fixed serialization for the wire contract, independent of the application's ObjectMapper. */
    static final ObjectMapper CONTRACT_JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final OutboxEventJpaRepository outbox;
    private final AggregateVersionJpaRepository versions;
    private final EventContext context;
    private final Clock clock;

    @Autowired
    public OutboxHcpEventPublisher(OutboxEventJpaRepository outbox, AggregateVersionJpaRepository versions,
                                   ObjectProvider<EventContext> context) {
        this(outbox, versions, context.getIfAvailable(() -> EventContext.NONE), Clock.systemUTC());
    }

    OutboxHcpEventPublisher(OutboxEventJpaRepository outbox, AggregateVersionJpaRepository versions,
                            EventContext context, Clock clock) {
        this.outbox = outbox;
        this.versions = versions;
        this.context = context;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(HcpDomainEvent event) {
        HcpIntegrationEvents.Mapped mapped = HcpIntegrationEvents.map(event);
        long version = nextVersion(mapped.aggregateId());

        EventEnvelope<HcpIntegrationEvents.HcpEventData> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                mapped.eventType(),
                HcpIntegrationEvents.SCHEMA_VERSION,
                mapped.aggregateId(),
                version,
                clock.instant(), // same transaction as the change: this is when it happened
                PRODUCER,
                context.correlationId(),
                context.actor(),
                mapped.data());

        outbox.save(OutboxEventEntity.pending(envelope.eventId(), HcpIntegrationEvents.AGGREGATE_TYPE,
                envelope.aggregateId(), version, envelope.eventType(), toJson(envelope), envelope.occurredAt()));
    }

    private long nextVersion(String aggregateId) {
        String key = HcpIntegrationEvents.AGGREGATE_TYPE + ":" + aggregateId;
        AggregateVersionEntity row = versions.findForUpdate(key)
                .orElseGet(() -> versions.save(new AggregateVersionEntity(key)));
        return row.next();
    }

    private static String toJson(EventEnvelope<?> envelope) {
        try {
            return CONTRACT_JSON.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize integration event " + envelope.eventType(), e);
        }
    }
}
