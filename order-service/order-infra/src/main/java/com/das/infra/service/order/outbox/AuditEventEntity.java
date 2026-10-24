package com.das.infra.service.order.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.das.cleanddd.domain.shared.bus.event.EventEnvelope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Audit trail (ALCOA+): one row per integration event, written in the same transaction as the change it records and
 * kept permanently. Unlike {@link OutboxEventEntity}, which is purged once published, this row is never updated or
 * deleted ({@link Immutable}; {@code audit-postgresql.sql} also refuses UPDATE, DELETE and TRUNCATE in the
 * database).
 *
 * <p>{@code payload} is the event envelope exactly as published, so the record answers who ({@code actor}, the
 * authenticated principal; {@code null} for steps a service takes on its own, such as the stock saga, which
 * {@code producer} and {@code correlationId} attribute), what ({@code eventType} and the data), when
 * ({@code occurredAt}) and in which order ({@code aggregateVersion}, unique per aggregate, so a gap is
 * detectable). For an edit, {@code changes} lists each changed field with its previous and new value (JSON
 * {@code [{"field","from","to"}]}), including fields the published event leaves out; {@code null} for other events.
 */
@Entity
@Immutable
@Table(name = "audit_event",
        uniqueConstraints = @UniqueConstraint(name = "ux_audit_event_aggregate_version",
                columnNames = {"aggregate_type", "aggregate_id", "aggregate_version"}),
        indexes = {
                @Index(name = "ix_audit_event_actor", columnList = "actor, occurred_at"),
                @Index(name = "ix_audit_event_occurred", columnList = "occurred_at")
        })
public class AuditEventEntity {

    @Id
    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 64)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private long aggregateVersion;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "producer", nullable = false, updatable = false, length = 50)
    private String producer;

    @Column(name = "actor", updatable = false, length = 255)
    private String actor;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "payload", nullable = false, updatable = false, length = OutboxEventEntity.MAX_PAYLOAD_LENGTH)
    private String payload;

    @Column(name = "changes", updatable = false, length = OutboxEventEntity.MAX_PAYLOAD_LENGTH)
    private String changes;

    protected AuditEventEntity() {
        // JPA
    }

    static AuditEventEntity of(String aggregateType, EventEnvelope<?> envelope, String payload) {
        return of(aggregateType, envelope, payload, null);
    }

    /** @param changes JSON list of the edit's field changes, or {@code null} */
    static AuditEventEntity of(String aggregateType, EventEnvelope<?> envelope, String payload, String changes) {
        if (changes != null && changes.length() > OutboxEventEntity.MAX_PAYLOAD_LENGTH) {
            throw new IllegalStateException("Audit changes exceed " + OutboxEventEntity.MAX_PAYLOAD_LENGTH + " characters");
        }
        AuditEventEntity e = new AuditEventEntity();
        e.eventId = envelope.eventId();
        e.aggregateType = aggregateType;
        e.aggregateId = envelope.aggregateId();
        e.aggregateVersion = envelope.aggregateVersion();
        e.eventType = envelope.eventType();
        e.producer = envelope.producer();
        e.actor = envelope.actor();
        e.correlationId = envelope.correlationId();
        e.occurredAt = envelope.occurredAt();
        e.payload = payload;
        e.changes = changes;
        return e;
    }

    public UUID getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getEventType() { return eventType; }
    public String getProducer() { return producer; }
    public String getActor() { return actor; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPayload() { return payload; }
    public String getChanges() { return changes; }
}
