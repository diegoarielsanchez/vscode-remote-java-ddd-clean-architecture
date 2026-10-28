package com.das.cleanddd.domain.order.events;

import java.util.List;

/**
 * The goods arrived but transport did not keep their storage conditions: they are quarantined at the receiver until
 * Quality releases or rejects them. {@code breaches} says how (empty when only the receiver reported it).
 */
public record OrderDeliveryQuarantinedEvent(String id, List<String> breaches, String note) implements OrderDomainEvent {

    public OrderDeliveryQuarantinedEvent {
        breaches = breaches == null ? List.of() : List.copyOf(breaches);
    }
}
