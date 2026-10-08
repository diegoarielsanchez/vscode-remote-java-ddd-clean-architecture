package com.das.infra.service.settlement.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MsrEventTranslatorTest {

    private static final String EVENT_ID = "3f1c2b9e-6a57-4f0a-9d1e-2b7c8e4a1f00";
    static final String MSR_ID = "5b1d6c2e-0f4a-4e7b-9c3d-2a8f6e1b7c90";

    private final MsrEventTranslator translator = new MsrEventTranslator();

    static String envelope(String eventId, String eventType, long version, String data) {
        return """
            {"eventId":"%s","eventType":"%s","schemaVersion":1,"aggregateId":"%s","aggregateVersion":%d,
             "occurredAt":"2026-10-08T10:00:00Z","producer":"medical-sales-rep-service","data":%s}
            """.formatted(eventId, eventType, MSR_ID, version, data);
    }

    private MsrStatusChange translate(String json) {
        return translator.translate(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertRejected(String json, String reasonFragment) {
        InvalidIntegrationEventException e = assertThrows(InvalidIntegrationEventException.class, () -> translate(json));
        assertTrue(e.getMessage().contains(reasonFragment), e.getMessage());
    }

    @Test
    void readsOnlyTheActiveStatus() {
        MsrStatusChange c = translate(envelope(EVENT_ID, "msr.created", 1, "{\"name\":\"Rita\",\"surname\":\"Paz\",\"active\":true}"));

        assertEquals(UUID.fromString(EVENT_ID), c.eventId());
        assertEquals("msr.created", c.eventType());
        assertEquals(MSR_ID, c.msrId());
        assertEquals(1, c.version());
        assertTrue(c.active());
    }

    @Test
    void statusEventsDetermineActiveFromTheirType() {
        assertFalse(translate(envelope(EVENT_ID, "msr.deactivated", 2, "{}")).active());
        assertTrue(translate(envelope(EVENT_ID, "msr.activated", 3, "{}")).active());
    }

    @Test
    void namesAreNotNeededSoTheyAreNotRequired() {
        assertFalse(translate(envelope(EVENT_ID, "msr.updated", 4, "{\"active\":false}")).active());
    }

    @Test
    void rejectsWhatSettlementCannotTrust() {
        assertRejected(envelope(EVENT_ID, "msr.created", 1, "{\"active\":\"yes\"}"), "active");
        assertRejected(envelope(EVENT_ID, "msr.deleted", 1, "{}"), "eventType");
        assertRejected(envelope("not-a-uuid", "msr.activated", 1, "{}"), "eventId");
        assertRejected(envelope(EVENT_ID, "msr.activated", 0, "{}"), "aggregateVersion");
        assertRejected(envelope(EVENT_ID, "msr.activated", 1, "{}").replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "schemaVersion");
        assertRejected("{\"eventType\":\"MSR_DEACTIVATED\",\"id\":\"" + MSR_ID + "\"}", "schemaVersion");
        assertRejected("{\"pad\":\"" + "x".repeat(EnvelopeParser.MAX_MESSAGE_BYTES) + "\"}", "exceeds");
    }
}
