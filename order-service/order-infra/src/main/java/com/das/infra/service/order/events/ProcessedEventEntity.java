package com.das.infra.service.order.events;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Idempotency record: an integration event whose effects have been applied (inbox pattern). */
@Entity
@Table(name = "processed_event")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventEntity() {
        // JPA
    }

    public ProcessedEventEntity(String eventId, String eventType, Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getProcessedAt() { return processedAt; }
}
