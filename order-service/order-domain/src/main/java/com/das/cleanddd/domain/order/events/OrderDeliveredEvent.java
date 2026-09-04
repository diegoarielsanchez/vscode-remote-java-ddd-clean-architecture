package com.das.cleanddd.domain.order.events;

public record OrderDeliveredEvent(
        String id) implements OrderDomainEvent {
}
