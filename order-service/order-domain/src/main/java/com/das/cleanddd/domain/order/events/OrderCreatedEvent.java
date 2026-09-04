package com.das.cleanddd.domain.order.events;

import java.math.BigDecimal;

public record OrderCreatedEvent(
        String id,
        String medicalSalesRepId,
        int lineCount,
        BigDecimal totalAmount) implements OrderDomainEvent {
}
