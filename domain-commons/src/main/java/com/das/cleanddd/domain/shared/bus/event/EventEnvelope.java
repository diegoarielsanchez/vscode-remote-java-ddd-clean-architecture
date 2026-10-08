package com.das.cleanddd.domain.shared.bus.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Integration-event contract published between bounded contexts (shared kernel).
 *
 * <p>Domain events stay private to their bounded context; what crosses the broker is this
 * envelope. Consumers rely on:
 * <ul>
 *   <li>{@code eventId} — unique per event, used to deduplicate at-least-once delivery;</li>
 *   <li>{@code aggregateVersion} — strictly increasing per aggregate, used to discard stale or
 *       out-of-order events;</li>
 *   <li>{@code schemaVersion} — bumped on breaking changes to {@code data}; additive changes
 *       keep the same version.</li>
 * </ul>
 * {@code actor} identifies who caused the change (for audit) and must never carry credentials;
 * {@code data} must contain only what consumers need (no secrets, minimal personal data).
 *
 * @param <T> the event-specific payload
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        String actor,
        T data) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        requireText(eventType, "eventType");
        requireText(aggregateId, "aggregateId");
        requireText(producer, "producer");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(data, "data");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
        if (aggregateVersion < 1) throw new IllegalArgumentException("aggregateVersion must be >= 1");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
