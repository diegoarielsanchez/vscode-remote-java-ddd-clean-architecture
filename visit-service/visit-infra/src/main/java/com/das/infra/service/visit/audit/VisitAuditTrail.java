package com.das.infra.service.visit.audit;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.shared.audit.ChangeTracked;
import com.das.cleanddd.domain.shared.bus.event.EventEnvelope;
import com.das.cleanddd.domain.visit.events.VisitDomainEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes visit and visit plan changes to the audit trail, inside the caller's transaction ({@code MANDATORY}:
 * the repositories call it while saving, so the change and its audit entry commit or roll back together).
 * Unlike the other services' events, nothing is published to the broker.
 */
@Service
public class VisitAuditTrail {

    public static final String PRODUCER = "visit-service";
    static final int SCHEMA_VERSION = 1;

    /** Fixed serialization for the audit record, independent of the application's ObjectMapper. */
    static final ObjectMapper AUDIT_JSON = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final AuditEventJpaRepository audit;
    private final AuditAggregateVersionJpaRepository versions;
    private final EventContext context;
    private final Clock clock;

    @Autowired
    public VisitAuditTrail(AuditEventJpaRepository audit, AuditAggregateVersionJpaRepository versions,
                           ObjectProvider<EventContext> context) {
        this(audit, versions, context.getIfAvailable(() -> EventContext.NONE), Clock.systemUTC());
    }

    VisitAuditTrail(AuditEventJpaRepository audit, AuditAggregateVersionJpaRepository versions, EventContext context,
                    Clock clock) {
        this.audit = audit;
        this.versions = versions;
        this.context = context;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(VisitDomainEvent event) {
        long version = nextVersion(event.aggregateType(), event.aggregateId());
        EventEnvelope<VisitDomainEvent> envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                event.eventType(),
                SCHEMA_VERSION,
                event.aggregateId(),
                version,
                clock.instant(), // same transaction as the change: this is when it happened
                PRODUCER,
                context.correlationId(),
                context.actor(),
                event);
        audit.save(AuditEventEntity.of(envelope.eventId(), event.aggregateType(), event.aggregateId(), version,
                event.eventType(), PRODUCER, envelope.actor(), envelope.correlationId(), envelope.occurredAt(),
                toJson(envelope), event instanceof ChangeTracked tracked ? toJson(tracked.changes()) : null,
                event instanceof ChangeTracked tracked ? tracked.reason() : null));
    }

    private long nextVersion(String aggregateType, String aggregateId) {
        String key = aggregateType + ":" + aggregateId;
        AuditAggregateVersionEntity row = versions.findForUpdate(key)
                .orElseGet(() -> versions.save(new AuditAggregateVersionEntity(key)));
        return row.next();
    }

    private static String toJson(Object value) {
        try {
            return AUDIT_JSON.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize audit entry " + value.getClass().getSimpleName(), e);
        }
    }
}
