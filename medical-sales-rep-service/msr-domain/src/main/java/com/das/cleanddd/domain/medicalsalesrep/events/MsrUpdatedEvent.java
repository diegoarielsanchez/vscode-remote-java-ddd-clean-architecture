package com.das.cleanddd.domain.medicalsalesrep.events;

import java.util.List;

import com.das.cleanddd.domain.shared.audit.ChangeTracked;
import com.das.cleanddd.domain.shared.audit.FieldChange;

/**
 * {@code changes} lists the fields the edit changed with their previous values, for the audit trail only: it is
 * not published, so it may include fields other services never see.
 */
public record MsrUpdatedEvent(
        String id,
        String name,
        String surname,
        String email,
        Boolean active,
        List<FieldChange> changes) implements MsrDomainEvent, ChangeTracked {

    public MsrUpdatedEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    /** Without the changes. */
    public MsrUpdatedEvent(String id, String name, String surname, String email, Boolean active) {
        this(id, name, surname, email, active, List.of());
    }
}
