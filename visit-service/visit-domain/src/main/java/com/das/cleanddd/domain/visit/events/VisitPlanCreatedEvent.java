package com.das.cleanddd.domain.visit.events;

import java.time.LocalDateTime;
import java.util.List;

/** A visit was planned. {@code items} are the products to bring, as {@code "<productId> x <quantity>"}. */
public record VisitPlanCreatedEvent(
        String visitPlanId,
        LocalDateTime visitDateTime,
        String healthCareProfId,
        String medicalSalesRepId,
        String visitSiteId,
        String visitComments,
        List<String> items) implements VisitDomainEvent {

    public VisitPlanCreatedEvent {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Override public String aggregateType() { return VISIT_PLAN; }
    @Override public String aggregateId() { return visitPlanId; }
    @Override public String eventType() { return "visit-plan.created"; }
}
