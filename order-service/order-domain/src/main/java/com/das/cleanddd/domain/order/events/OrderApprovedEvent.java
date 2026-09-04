package com.das.cleanddd.domain.order.events;

public record OrderApprovedEvent(
        String id,
        String approvedBy) implements OrderDomainEvent {
}
