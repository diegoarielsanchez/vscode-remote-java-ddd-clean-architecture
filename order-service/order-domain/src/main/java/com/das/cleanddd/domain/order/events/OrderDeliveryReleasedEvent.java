package com.das.cleanddd.domain.order.events;

/** Quality released a quarantined delivery: the goods may be used. An {@link OrderDeliveredEvent} follows. */
public record OrderDeliveryReleasedEvent(String id, String decidedBy, String reason) implements OrderDomainEvent {
}
