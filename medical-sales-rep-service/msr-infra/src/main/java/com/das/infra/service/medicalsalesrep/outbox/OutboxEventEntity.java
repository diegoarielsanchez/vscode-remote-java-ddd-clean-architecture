package com.das.infra.service.medicalsalesrep.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Transactional outbox row: written in the same transaction as the aggregate change, published to
 * RabbitMQ later by {@link MsrOutboxRelay}. {@code id} is the integration event's {@code eventId}.
 */
@Entity
@Table(name = "outbox_event", indexes = @Index(name = "ix_outbox_event_pending", columnList = "published_at, occurred_at"))
public class OutboxEventEntity {

    static final int MAX_PAYLOAD_LENGTH = 8000;
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, length = MAX_PAYLOAD_LENGTH)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected OutboxEventEntity() {
        // JPA
    }

    static OutboxEventEntity pending(UUID id, String aggregateType, String aggregateId, long aggregateVersion,
                                     String eventType, String payload, Instant occurredAt) {
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new IllegalStateException("Integration event payload exceeds " + MAX_PAYLOAD_LENGTH + " characters");
        }
        OutboxEventEntity e = new OutboxEventEntity();
        e.id = id;
        e.aggregateType = aggregateType;
        e.aggregateId = aggregateId;
        e.aggregateVersion = aggregateVersion;
        e.eventType = eventType;
        e.payload = payload;
        e.occurredAt = occurredAt;
        return e;
    }

    void markPublished(Instant at) {
        this.publishedAt = at;
        this.lastError = null;
    }

    void recordFailure(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
}
