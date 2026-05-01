package com.das.inframySQL.service.medicalsalesrep;

/**
 * Flat JSON payload sent to RabbitMQ. Mirrors all MSR snapshot fields so
 * consumers can upsert without an extra lookup.
 */
public record MsrEventPayload(
        String eventType,
        String id,
        String name,
        String surname,
        String email,
        Boolean active,
        String occurredAt) {
}
