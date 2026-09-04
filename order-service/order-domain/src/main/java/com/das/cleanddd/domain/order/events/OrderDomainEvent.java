package com.das.cleanddd.domain.order.events;

public sealed interface OrderDomainEvent
        permits OrderCreatedEvent, OrderSubmittedForApprovalEvent, OrderApprovedEvent, OrderRejectedEvent,
        OrderDeliveredEvent {
}
