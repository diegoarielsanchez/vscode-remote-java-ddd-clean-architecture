package com.das.cleanddd.domain.order.events;

public record OrderSubmittedForApprovalEvent(
        String id) implements OrderDomainEvent {
}
