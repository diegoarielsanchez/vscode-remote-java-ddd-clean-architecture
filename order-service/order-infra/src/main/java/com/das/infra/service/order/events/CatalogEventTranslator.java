package com.das.infra.service.order.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.das.cleanddd.domain.order.entities.Order;
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
                        e.nonNegativeDecimal(line, "unitPrice")));
            }
            return Optional.of(new StockReservationReply.Confirmed(e.eventId(), e.eventType(), e.aggregateId(), lines));
        }
        return Optional.of(new StockReservationReply.Rejected(e.eventId(), e.eventType(), e.aggregateId(),
                e.text(e.data(), "reason", EnvelopeParser.MAX_REASON_LENGTH)));
    }
}
