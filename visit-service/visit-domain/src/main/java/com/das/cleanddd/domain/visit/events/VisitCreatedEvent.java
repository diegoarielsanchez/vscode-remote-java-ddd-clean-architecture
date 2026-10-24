package com.das.cleanddd.domain.visit.events;

import java.time.LocalDateTime;
import java.util.List;

/** A visit was recorded. {@code items} are the products handed out, as {@code "<productId> x <quantity>"}. */
public record VisitCreatedEvent(
        String visitId,
        LocalDateTime visitDate,
        String healthCareProfId,
        String medicalSalesRepId,
        String visitSiteId,
        String visitComments,
        List<String> items) implements VisitDomainEvent {

    public VisitCreatedEvent {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Override public String aggregateType() { return VISIT; }
    @Override public String aggregateId() { return visitId; }
    @Override public String eventType() { return "visit.created"; }
}
