package com.das.cleanddd.domain.visit.events;

import java.util.List;

import com.das.cleanddd.domain.shared.audit.ChangeTracked;
import com.das.cleanddd.domain.shared.audit.FieldChange;

/** A visit plan was edited: each changed field with its previous and new value, and why. */
public record VisitPlanUpdatedEvent(String visitPlanId, List<FieldChange> changes, String reason)
        implements VisitDomainEvent, ChangeTracked {

    public VisitPlanUpdatedEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    @Override public String aggregateType() { return VISIT_PLAN; }
    @Override public String aggregateId() { return visitPlanId; }
    @Override public String eventType() { return "visit-plan.updated"; }
}
