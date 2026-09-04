package com.das.infra.service.order;

import java.math.BigDecimal;

/** Flat JSON payload sent to RabbitMQ. Fields not applicable to a given event type are left null. */
public record OrderEventPayload(
        String eventType,
        String id,
        String medicalSalesRepId,
        Integer lineCount,
        BigDecimal totalAmount,
        String actor,
        String reason,
        String occurredAt) {
}
