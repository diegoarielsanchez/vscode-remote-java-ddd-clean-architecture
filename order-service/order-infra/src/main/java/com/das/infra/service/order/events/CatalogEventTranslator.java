package com.das.infra.service.order.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.shared.storage.StorageCondition;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Anti-corruption layer for {@code catalog.events} (schema version 1). Only the stock saga's answers
 * matter here; the queue receives every {@code catalog.*} event (so each one is routable and no
 * producer outbox stalls), and any other valid catalog event is skipped. The reservation's aggregate
 * id is the order id.
 */
@Component
public class CatalogEventTranslator {

    static final String CONFIRMED = "catalog.reservation.confirmed";
    static final String REJECTED = "catalog.reservation.rejected";
    private static final Set<String> TYPES = Set.of(CONFIRMED, REJECTED);

    /** @return empty for a valid catalog event this service does not act on */
    public Optional<StockReservationReply> translate(byte[] body) {
        EnvelopeParser.Envelope e = EnvelopeParser.parse(body, "catalog", null);
        if (!TYPES.contains(e.eventType())) {
            if (e.eventType().startsWith("catalog.")) return Optional.empty();
            throw e.invalid("unsupported eventType");
        }
        if (CONFIRMED.equals(e.eventType())) {
            List<StockReservationReply.PricedLine> lines = new ArrayList<>();
            for (JsonNode line : e.array(e.data(), "lines", Order.MAX_LINES)) {
                lines.add(new StockReservationReply.PricedLine(
                        e.uuidText(line, "productId"),
                        e.text(line, "productName", EnvelopeParser.MAX_NAME_LENGTH),
                        e.nonNegativeDecimal(line, "unitPrice"),
                        storageCondition(e, line)));
            }
            return Optional.of(new StockReservationReply.Confirmed(e.eventId(), e.eventType(), e.aggregateId(), lines));
        }
        return Optional.of(new StockReservationReply.Rejected(e.eventId(), e.eventType(), e.aggregateId(),
                e.text(e.data(), "reason", EnvelopeParser.MAX_REASON_LENGTH)));
    }

    /** Optional: left out (or null) when the product has none; otherwise it must be a valid condition. */
    private static StorageCondition storageCondition(EnvelopeParser.Envelope e, JsonNode line) {
        JsonNode node = line.get("storageCondition");
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw e.invalid("storageCondition must be an object");
        }
        JsonNode min = node.get("minTemperatureC");
        JsonNode max = node.get("maxTemperatureC");
        JsonNode humidity = node.get("maxHumidityPercent");
        JsonNode light = node.get("protectFromLight");
        if (min == null || !min.isNumber() || max == null || !max.isNumber()) {
            throw e.invalid("storageCondition temperatures must be numbers");
        }
        if (humidity != null && !humidity.isNull() && !humidity.canConvertToInt()) {
            throw e.invalid("storageCondition.maxHumidityPercent must be an integer");
        }
        if (light != null && !light.isNull() && !light.isBoolean()) {
            throw e.invalid("storageCondition.protectFromLight must be a boolean");
        }
        try {
            return new StorageCondition(min.decimalValue(), max.decimalValue(),
                    humidity == null || humidity.isNull() ? null : humidity.intValue(),
                    light != null && light.booleanValue());
        } catch (IllegalArgumentException ex) {
            throw e.invalid("storageCondition: " + ex.getMessage());
        }
    }
}
