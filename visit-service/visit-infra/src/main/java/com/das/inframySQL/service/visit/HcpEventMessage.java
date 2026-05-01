package com.das.inframySQL.service.visit;

/**
 * Local deserialization record for HCP domain events consumed from
 * the {@code hcp.events} topic exchange.
 * Mirrors {@code HcpEventPayload} from hcp-infra.
 */
public record HcpEventMessage(
        String eventType,
        String id,
        String name,
        String surname,
        String email,
        Boolean active,
        String occurredAt) {}
