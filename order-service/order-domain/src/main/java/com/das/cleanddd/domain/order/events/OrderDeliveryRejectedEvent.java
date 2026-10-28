package com.das.cleanddd.domain.order.events;

/** Quality rejected a quarantined delivery: the goods cannot be used and await destruction. The order ends here. */
public record OrderDeliveryRejectedEvent(String id, String decidedBy, String reason) implements OrderDomainEvent {
}
