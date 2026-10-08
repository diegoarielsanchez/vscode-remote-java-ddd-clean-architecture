package com.das.infra.service.catalog.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Anti-corruption layer for {@code order.events} (schema version 1). Only three events move the
 * stock saga; the queue receives every {@code order.*} event (so each one is routable and the order
 * outbox never stalls), and any other valid order event is skipped.
 */
@Component
public class OrderEventTranslator {

    static final String CREATED = "order.created";
    static final String REJECTED = "order.rejected";
    static final String DELIVERED = "order.delivered";
    /** Same cap as order-service's {@code Order.MAX_LINES}. */
    static final int MAX_LINES = 20;
    static final int MAX_QUANTITY = 1_000_000;

    /** @return empty for a valid order event the catalog does not act on */
    public Optional<OrderSagaEvent> translate(byte[] body) {
        EnvelopeParser.Envelope e = EnvelopeParser.parse(body, "order", null);
        return switch (e.eventType()) {
            case CREATED -> Optional.of(new OrderSagaEvent.Created(e.eventId(), e.eventType(), e.aggregateId(), lines(e)));
            case REJECTED -> Optional.of(new OrderSagaEvent.Rejected(e.eventId(), e.eventType(), e.aggregateId()));
            case DELIVERED -> Optional.of(new OrderSagaEvent.Delivered(e.eventId(), e.eventType(), e.aggregateId()));
            default -> {
                if (e.eventType().startsWith("order.")) yield Optional.empty();
                throw e.invalid("unsupported eventType");
            }
        };
    }

    private static List<OrderSagaEvent.Line> lines(EnvelopeParser.Envelope e) {
        List<OrderSagaEvent.Line> lines = new ArrayList<>();
        for (JsonNode line : e.array(e.data(), "lines", MAX_LINES)) {
            int quantity = e.positiveInt(line, "quantity");
            if (quantity > MAX_QUANTITY) throw e.invalid("quantity exceeds " + MAX_QUANTITY);
            lines.add(new OrderSagaEvent.Line(e.uuidText(line, "productId"), quantity));
        }
        return lines;
    }
}
