package com.das.cleanddd.domain.visit.events;

import java.util.List;

import com.das.cleanddd.domain.shared.audit.ChangeTracked;
import com.das.cleanddd.domain.shared.audit.FieldChange;

/** A visit was edited: each changed field with its previous and new value, and why. */
public record VisitUpdatedEvent(String visitId, List<FieldChange> changes, String reason) implements VisitDomainEvent, ChangeTracked {

    public VisitUpdatedEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    @Override public String aggregateType() { return VISIT; }
    @Override public String aggregateId() { return visitId; }
    @Override public String eventType() { return "visit.updated"; }
}
