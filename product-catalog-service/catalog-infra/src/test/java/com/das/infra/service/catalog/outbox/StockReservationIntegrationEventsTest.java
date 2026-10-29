package com.das.infra.service.catalog.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationConfirmedEvent;
import com.das.cleanddd.domain.shared.storage.StorageCondition;
import tools.jackson.databind.JsonNode;

class StockReservationIntegrationEventsTest {

    @Test
    void aConfirmedLineCarriesTheConditionsItsUnitsMustTravelUnder() throws Exception {
        StockReservationConfirmedEvent confirmed = new StockReservationConfirmedEvent("order-1", List.of(
                new ReservationLine("p-1", 4, "Insulin 100IU", new BigDecimal("20.00"), List.of(),
                        new StorageCondition(new BigDecimal("2"), new BigDecimal("8"), null, true)),
                new ReservationLine("p-2", 10, "Brochure", BigDecimal.ZERO)));

        JsonNode data = OutboxProductEventPublisher.CONTRACT_JSON.readTree(OutboxProductEventPublisher.CONTRACT_JSON
                .writeValueAsString(StockReservationIntegrationEvents.map(confirmed).data()));

        JsonNode cold = data.at("/lines/0/storageCondition");
        assertEquals("2.0", cold.get("minTemperatureC").asString());
        assertEquals("8.0", cold.get("maxTemperatureC").asString());
        assertTrue(cold.get("coldChain").asBoolean());
        assertTrue(cold.get("protectFromLight").asBoolean());
        assertFalse(data.at("/lines/1").has("storageCondition"), "left out when the product has none");
    }
}
