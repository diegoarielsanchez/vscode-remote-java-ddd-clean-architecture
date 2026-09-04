package com.das.cleanddd.domain.order.events;

public record OrderRejectedEvent(
        String id,
        String rejectedBy,
        String reason) implements OrderDomainEvent {
}
