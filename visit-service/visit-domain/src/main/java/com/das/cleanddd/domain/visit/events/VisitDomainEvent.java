package com.das.cleanddd.domain.visit.events;

/**
 * A change to a visit or visit plan, kept in the service's audit trail (ALCOA+). Visit-service publishes no events
 * to other services: these are recorded, in the same transaction as the change, only for the audit trail.
 */
public sealed interface VisitDomainEvent permits VisitCreatedEvent, VisitUpdatedEvent, VisitAttachmentAddedEvent,
        VisitPlanCreatedEvent, VisitPlanUpdatedEvent, VisitPlanDeactivatedEvent {

    String VISIT = "visit";
    String VISIT_PLAN = "visit-plan";

    /** {@link #VISIT} or {@link #VISIT_PLAN}. */
    String aggregateType();

    String aggregateId();

    /** e.g. {@code visit.created}. */
    String eventType();
}
