package com.das.infra.service.order.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CatalogEventTranslatorTest {

    private static final String EVENT_ID = "3f1c2b9e-6a57-4f0a-9d1e-2b7c8e4a1f00";
    static final String ORDER_ID = "8a6e0804-2bd0-4672-b79d-d97027f9071a";
    static final String PRODUCT_ID = "0d4c8a3e-6b1f-4f27-9e5a-3c2b1a0f9e8d";

    private final CatalogEventTranslator translator = new CatalogEventTranslator();

    static String envelope(String eventId, String eventType, String data) {
        return """
            {"eventId":"%s","eventType":"%s","schemaVersion":1,"aggregateId":"%s","aggregateVersion":1,
             "occurredAt":"2026-10-08T10:00:00Z","producer":"product-catalog-service","data":%s}
            """.formatted(eventId, eventType, ORDER_ID, data);
    }

    static String confirmedData(String line) {
        return "{\"orderId\":\"" + ORDER_ID + "\",\"lines\":[" + line + "]}";
    }

    static String line(String productId, String name, String unitPrice) {
        return "{\"productId\":\"%s\",\"productName\":\"%s\",\"quantity\":2,\"unitPrice\":%s}"
                .formatted(productId, name, unitPrice);
    }

    private StockReservationReply translate(String json) {
        return translator.translate(json.getBytes(StandardCharsets.UTF_8)).orElseThrow();
    }

    private void assertRejected(String json, String reasonFragment) {
        InvalidIntegrationEventException e = assertThrows(InvalidIntegrationEventException.class, () -> translate(json));
        assertTrue(e.getMessage().contains(reasonFragment), e.getMessage());
    }

    @Test
    void readsAConfirmedReservation() {
        var reply = assertInstanceOf(StockReservationReply.Confirmed.class, translate(envelope(EVENT_ID,
                "catalog.reservation.confirmed", confirmedData(line(PRODUCT_ID, "Amoxicillin 500mg", "12.50")))));

        assertEquals(UUID.fromString(EVENT_ID), reply.eventId());
        assertEquals(ORDER_ID, reply.orderId());
        assertEquals(1, reply.lines().size());
        assertEquals(PRODUCT_ID, reply.lines().get(0).productId());
        assertEquals("Amoxicillin 500mg", reply.lines().get(0).productName());
        assertEquals(0, new BigDecimal("12.50").compareTo(reply.lines().get(0).unitPrice()));
    }

    @Test
    void readsARejectedReservation() {
        var reply = assertInstanceOf(StockReservationReply.Rejected.class, translate(envelope(EVENT_ID,
                "catalog.reservation.rejected", "{\"orderId\":\"" + ORDER_ID + "\",\"reason\":\"Insufficient stock\"}")));

        assertEquals(ORDER_ID, reply.orderId());
        assertEquals("Insufficient stock", reply.reason());
    }

    @Test
    void rejectsWhatOrderServiceCannotTrust() {
        String confirmed = "catalog.reservation.confirmed";
        assertRejected(envelope(EVENT_ID, "visit.created", "{}"), "eventType");
        assertRejected(envelope(EVENT_ID, "Catalog.Reservation.Confirmed", "{}"), "eventType");
        assertRejected(envelope(EVENT_ID, confirmed, "{\"lines\":[]}"), "lines");
        assertRejected(envelope(EVENT_ID, confirmed, "{\"lines\":[1]}"), "lines");
        assertRejected(envelope(EVENT_ID, confirmed, confirmedData(line("not-a-uuid", "X", "1.00"))), "productId");
        assertRejected(envelope(EVENT_ID, confirmed, confirmedData(line(PRODUCT_ID, "X", "-1.00"))), "unitPrice");
        assertRejected(envelope(EVENT_ID, confirmed, confirmedData(line(PRODUCT_ID, "X", "\"1.00\""))), "unitPrice");
        assertRejected(envelope(EVENT_ID, confirmed, confirmedData(line(PRODUCT_ID, "X", "1e400"))), "unitPrice");
        assertRejected(envelope(EVENT_ID, confirmed, confirmedData(line(PRODUCT_ID, "X", "1.00001"))), "unitPrice");
        assertRejected(envelope(EVENT_ID, confirmed, confirmedData(line(PRODUCT_ID, "a\\u0000b", "1.00"))), "productName");
        assertRejected(envelope(EVENT_ID, confirmed, confirmedData(line(PRODUCT_ID, "x".repeat(201), "1.00"))), "productName");
        assertRejected(envelope(EVENT_ID, "catalog.reservation.rejected", "{}"), "reason");
        assertRejected(envelope("not-a-uuid", "catalog.reservation.rejected", "{\"reason\":\"r\"}"), "eventId");
        assertRejected("{\"eventId\":\"" + EVENT_ID + "\",\"eventId\":\"" + EVENT_ID + "\"}", "malformed");
    }

    @Test
    void skipsOtherCatalogEvents() {
        assertTrue(translator.translate(envelope(EVENT_ID, "catalog.reservation.released", "{}")
                .getBytes(StandardCharsets.UTF_8)).isEmpty());
        assertTrue(translator.translate(envelope(EVENT_ID, "catalog.product.restocked", "{\"stockDelta\":5}")
                .getBytes(StandardCharsets.UTF_8)).isEmpty());
        // ...but only valid ones: a broken envelope is still dead-lettered.
        assertRejected(envelope("bad", "catalog.product.restocked", "{}"), "eventId");
    }

    @Test
    void rejectsMoreLinesThanAnOrderCanHave() {
        String many = String.join(",", java.util.Collections.nCopies(21, line(PRODUCT_ID, "X", "1.00")));
        assertRejected(envelope(EVENT_ID, "catalog.reservation.confirmed", confirmedData(many)), "more than");
    }
}
