package com.das.cleanddd.domain.visit.events;

/**
 * A future visit plan was deactivated because its Healthcare Professional or Medical Sales Rep was deactivated.
 * {@code reason} says which, e.g. {@code "hcp <id> deactivated"}.
 */
public record VisitPlanDeactivatedEvent(String visitPlanId, String reason) implements VisitDomainEvent {

    @Override public String aggregateType() { return VISIT_PLAN; }
    @Override public String aggregateId() { return visitPlanId; }
    @Override public String eventType() { return "visit-plan.deactivated"; }
}
