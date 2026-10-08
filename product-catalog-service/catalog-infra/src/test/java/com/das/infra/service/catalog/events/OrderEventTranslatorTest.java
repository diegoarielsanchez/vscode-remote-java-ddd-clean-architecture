package com.das.infra.service.catalog.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrderEventTranslatorTest {

    private static final String EVENT_ID = "3f1c2b9e-6a57-4f0a-9d1e-2b7c8e4a1f00";
    static final String ORDER_ID = "8a6e0804-2bd0-4672-b79d-d97027f9071a";
    static final String PRODUCT_ID = "0d4c8a3e-6b1f-4f27-9e5a-3c2b1a0f9e8d";

    private final OrderEventTranslator translator = new OrderEventTranslator();

    static String envelope(String eventId, String eventType, String data) {
        return """
            {"eventId":"%s","eventType":"%s","schemaVersion":1,"aggregateId":"%s","aggregateVersion":1,
             "occurredAt":"2026-10-08T10:00:00Z","producer":"order-service","data":%s}
            """.formatted(eventId, eventType, ORDER_ID, data);
    }

    static String created(String lines) {
        return envelope(EVENT_ID, "order.created",
                "{\"medicalSalesRepId\":\"5b1d6c2e-0f4a-4e7b-9c3d-2a8f6e1b7c90\",\"lineCount\":1,\"lines\":[" + lines + "]}");
    }

    static String line(String productId, String quantity) {
        return "{\"productId\":\"%s\",\"quantity\":%s}".formatted(productId, quantity);
    }

    private Optional<OrderSagaEvent> translate(String json) {
        return translator.translate(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertRejected(String json, String reasonFragment) {
        InvalidIntegrationEventException e = assertThrows(InvalidIntegrationEventException.class, () -> translate(json));
        assertTrue(e.getMessage().contains(reasonFragment), e.getMessage());
    }

    @Test
    void readsTheLinesOfANewOrder() {
        var created = assertInstanceOf(OrderSagaEvent.Created.class, translate(created(line(PRODUCT_ID, "4"))).orElseThrow());

        assertEquals(UUID.fromString(EVENT_ID), created.eventId());
        assertEquals(ORDER_ID, created.orderId());
        assertEquals(List.of(new OrderSagaEvent.Line(PRODUCT_ID, 4)), created.lines());
    }

    @Test
    void readsRejectionAndDelivery() {
        assertInstanceOf(OrderSagaEvent.Rejected.class,
                translate(envelope(EVENT_ID, "order.rejected", "{\"decidedBy\":\"admin\",\"reason\":\"budget\"}")).orElseThrow());
        assertInstanceOf(OrderSagaEvent.Delivered.class, translate(envelope(EVENT_ID, "order.delivered", "{}")).orElseThrow());
    }

    @Test
    void skipsOtherOrderEvents() {
        assertTrue(translate(envelope(EVENT_ID, "order.submitted-for-approval", "{\"totalAmount\":12.5}")).isEmpty());
        assertTrue(translate(envelope(EVENT_ID, "order.approved", "{\"decidedBy\":\"admin\"}")).isEmpty());
        assertTrue(translate(envelope(EVENT_ID, "order.stock-rejected", "{\"reason\":\"x\"}")).isEmpty());
    }

    @Test
    void rejectsWhatTheCatalogCannotTrust() {
        assertRejected(envelope(EVENT_ID, "catalog.product.created", "{}"), "eventType");
        assertRejected(envelope("bad", "order.approved", "{}"), "eventId");
        assertRejected(created(""), "lines");
        assertRejected(created(line("not-a-uuid", "1")), "productId");
        assertRejected(created(line(PRODUCT_ID, "0")), "quantity");
        assertRejected(created(line(PRODUCT_ID, "1.5")), "quantity");
        assertRejected(created(line(PRODUCT_ID, "\"3\"")), "quantity");
        assertRejected(created(line(PRODUCT_ID, "99999999999")), "quantity");
        assertRejected(created(line(PRODUCT_ID, "1000001")), "quantity");
        assertRejected(created(String.join(",", Collections.nCopies(21, line(PRODUCT_ID, "1")))), "more than");
        assertRejected(envelope(EVENT_ID, "order.created", "{\"lines\":{}}"), "lines");
    }
}
