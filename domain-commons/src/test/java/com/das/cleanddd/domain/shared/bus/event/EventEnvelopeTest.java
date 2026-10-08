package com.das.cleanddd.domain.shared.bus.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    private static EventEnvelope<String> envelope(String type, int schema, String aggregateId, long version, String data) {
        return new EventEnvelope<>(UUID.randomUUID(), type, schema, aggregateId, version,
                Instant.parse("2026-10-08T10:00:00Z"), "test-service", "corr-1", "user-1", data);
    }

    @Test
    void acceptsAWellFormedEnvelope() {
        EventEnvelope<String> e = envelope("hcp.created", 1, "hcp-1", 1, "payload");
        assertEquals("hcp.created", e.eventType());
        assertEquals(1, e.aggregateVersion());
    }

    @Test
    void rejectsMissingOrInvalidContractFields() {
        assertThrows(IllegalArgumentException.class, () -> envelope(" ", 1, "hcp-1", 1, "x"));
        assertThrows(IllegalArgumentException.class, () -> envelope("hcp.created", 0, "hcp-1", 1, "x"));
        assertThrows(IllegalArgumentException.class, () -> envelope("hcp.created", 1, "", 1, "x"));
        assertThrows(IllegalArgumentException.class, () -> envelope("hcp.created", 1, "hcp-1", 0, "x"));
        assertThrows(NullPointerException.class, () -> envelope("hcp.created", 1, "hcp-1", 1, null));
    }
}
