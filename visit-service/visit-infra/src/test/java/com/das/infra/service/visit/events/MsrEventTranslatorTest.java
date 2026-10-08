package com.das.infra.service.visit.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MsrEventTranslatorTest {

    private static final String EVENT_ID = "3f1c2b9e-6a57-4f0a-9d1e-2b7c8e4a1f00";
    private static final String MSR_ID = "8a6e0804-2bd0-4672-b79d-d97027f9071a";

    private final MsrEventTranslator translator = new MsrEventTranslator();

    static String envelope(String eventType, long version, String data) {
        return """
            {"eventId":"%s","eventType":"%s","schemaVersion":1,"aggregateId":"%s","aggregateVersion":%d,
             "occurredAt":"2026-10-08T10:00:00Z","producer":"medical-sales-rep-service",
             "correlationId":"trace-1","actor":"admin","data":%s}
            """.formatted(EVENT_ID, eventType, MSR_ID, version, data);
    }

    private MsrChange translate(String json) {
        return translator.translate(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertRejected(String json, String reasonFragment) {
        InvalidIntegrationEventException e = assertThrows(InvalidIntegrationEventException.class, () -> translate(json));
        assertTrue(e.getMessage().contains(reasonFragment), e.getMessage());
    }

    @Test
    void translatesACreatedEvent() {
        MsrChange c = translate(envelope("msr.created", 1, "{\"name\":\"Ana\",\"surname\":\"Gómez\",\"active\":true}"));

        assertEquals(UUID.fromString(EVENT_ID), c.eventId());
        assertEquals(MsrChange.Type.CREATED, c.type());
        assertEquals(MSR_ID, c.msrId());
        assertEquals(1, c.version());
        assertEquals("Ana", c.name());
        assertEquals("Gómez", c.surname());
        assertTrue(c.active());
    }

    @Test
    void translatesStatusEventsWithoutDetails() {
        MsrChange off = translate(envelope("msr.deactivated", 3, "{\"active\":false}"));
        assertEquals(MsrChange.Type.DEACTIVATED, off.type());
        assertFalse(off.active());
        assertNull(off.name());

        assertTrue(translate(envelope("msr.activated", 4, "{\"active\":true}")).active());
    }

    @Test
    void ignoresUnknownExtraFieldsSoProducersCanEvolveCompatibly() {
        String json = envelope("msr.updated", 2, "{\"name\":\"Ana\",\"surname\":\"Ruiz\",\"active\":true,\"newField\":42}")
                .replace("\"producer\"", "\"futureTopLevel\":true,\"producer\"");
        assertEquals("Ruiz", translate(json).surname());
    }

    @Test
    void rejectsAnUnsupportedSchemaVersion() {
        assertRejected(envelope("msr.created", 1, "{}").replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "schemaVersion");
    }

    @Test
    void rejectsUnknownEventTypes() {
        assertRejected(envelope("msr.deleted", 1, "{\"active\":false}"), "eventType");
    }

    @Test
    void rejectsTheLegacyFlatPayload() {
        assertRejected("{\"eventType\":\"MSR_CREATED\",\"id\":\"" + MSR_ID + "\",\"name\":\"Ana\",\"active\":true}", "schemaVersion");
    }

    @Test
    void rejectsIdsThatAreNotUuids() {
        assertRejected(envelope("msr.activated", 1, "{\"active\":true}")
                .replace(MSR_ID, "com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId@ba94993a"), "aggregateId");
        assertRejected(envelope("msr.activated", 1, "{\"active\":true}").replace(EVENT_ID, "1"), "eventId");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveVersions(long version) {
        assertRejected(envelope("msr.activated", version, "{\"active\":true}"), "aggregateVersion");
    }

    @Test
    void rejectsCreatedEventsWithoutRequiredDetails() {
        assertRejected(envelope("msr.created", 1, "{\"surname\":\"Gómez\",\"active\":true}"), "name");
        assertRejected(envelope("msr.created", 1, "{\"name\":\"Ana\",\"surname\":\"Gómez\",\"active\":\"yes\"}"), "active");
    }

    @Test
    void rejectsOversizedOrControlCharacterNames() {
        assertRejected(envelope("msr.created", 1, "{\"name\":\"" + "x".repeat(101) + "\",\"surname\":\"G\",\"active\":true}"), "exceeds");
        assertRejected(envelope("msr.created", 1, "{\"name\":\"Ana\\nFAKE LOG\",\"surname\":\"G\",\"active\":true}"), "control");
    }

    @Test
    void rejectsMalformedOversizedOrNonObjectMessages() {
        assertRejected("{not json", "malformed");
        assertRejected("[1,2,3]", "not a JSON object");
        assertRejected("", "empty");
        assertRejected("{\"pad\":\"" + "x".repeat(MsrEventTranslator.MAX_MESSAGE_BYTES) + "\"}", "exceeds");
    }

    @Test
    void rejectsDuplicateKeysInsteadOfPickingOne() {
        assertRejected(envelope("msr.activated", 1, "{\"active\":true}")
                .replace("\"eventType\":\"msr.activated\"", "\"eventType\":\"msr.activated\",\"eventType\":\"msr.deactivated\""), "malformed");
    }
}
