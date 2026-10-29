package com.das.infra.service.catalog.outbox;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * One audit trail entry as a reviewer reads it: the record's change number, what happened, when, who, why
 * ({@code reason}, edits only), the edited fields with their previous and new values ({@code changes}, edits only)
 * and the data recorded with the entry.
 */
public record AuditEntryView(long version, String eventType, Instant occurredAt, String actor, String correlationId,
                             String reason, JsonNode changes, JsonNode data) {}
