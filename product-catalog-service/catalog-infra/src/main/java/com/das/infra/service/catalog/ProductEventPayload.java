package com.das.infra.service.catalog;

import java.math.BigDecimal;

/**
 * Flat JSON payload sent to RabbitMQ. Fields not applicable to a given event
 * type (e.g. name/description/price/unit on a stock-only event) are left null.
 */
public record ProductEventPayload(
        String eventType,
        String id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Boolean active,
        Integer stockDelta,
        Integer remainingStock,
        String occurredAt) {
}
