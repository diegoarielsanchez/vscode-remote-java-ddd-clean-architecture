package com.das.inframySQL.service.visit;

/**
 * Local deserialization record for MSR domain events consumed from
 * the {@code msr.events} topic exchange.
 * Mirrors {@code MsrEventPayload} from msr-infra.
 */
public record MsrEventMessage(
        String eventType,
        String id,
        String name,
        String surname,
        String email,
        Boolean active,
        String occurredAt) {}
