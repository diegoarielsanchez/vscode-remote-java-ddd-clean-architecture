package com.das.infra.service.visit.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Audit trail (ALCOA+): one row per change to a visit or visit plan, written in the same transaction as the change
 * and kept permanently. Never updated or deleted ({@link Immutable}; {@code audit-sqlserver.sql} also refuses
 * UPDATE, DELETE and TRUNCATE in the database).
 *
 * <p>Same columns as the audit trail of the other services. {@code payload} is the entry as a JSON envelope (who:
 * {@code actor}, the authenticated principal, {@code null} for steps the service takes on its own, such as
 * deactivating plans after an HCP or MSR event; what: {@code eventType} and the data; when: {@code occurredAt}).
 * {@code aggregateVersion} is unique per aggregate, so a gap is detectable. For an edit, {@code changes} lists each
 * changed field with its previous and new value.
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

    static final int MAX_PAYLOAD_LENGTH = 8000;

    @Id
    @Column(name = "event_id", updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 255)
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

    @Column(name = "payload", nullable = false, updatable = false, length = MAX_PAYLOAD_LENGTH)
    private String payload;

    @Column(name = "changes", updatable = false, length = MAX_PAYLOAD_LENGTH)
    private String changes;

    protected AuditEventEntity() {
        // JPA
    }

    static AuditEventEntity of(UUID eventId, String aggregateType, String aggregateId, long aggregateVersion,
                               String eventType, String producer, String actor, String correlationId,
                               Instant occurredAt, String payload, String changes) {
        if (payload.length() > MAX_PAYLOAD_LENGTH || (changes != null && changes.length() > MAX_PAYLOAD_LENGTH)) {
            throw new IllegalStateException("Audit entry exceeds " + MAX_PAYLOAD_LENGTH + " characters");
        }
        AuditEventEntity e = new AuditEventEntity();
        e.eventId = eventId;
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.aggregateVersion = aggregateVersion;
        e.eventType = eventType;
        e.producer = producer;
        e.actor = actor;
        e.correlationId = correlationId;
        e.occurredAt = occurredAt;
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
