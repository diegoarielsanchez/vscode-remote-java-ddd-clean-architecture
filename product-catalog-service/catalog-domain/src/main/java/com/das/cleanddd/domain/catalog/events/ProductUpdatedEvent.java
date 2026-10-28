package com.das.cleanddd.domain.catalog.events;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.shared.audit.ChangeTracked;
import com.das.cleanddd.domain.shared.audit.FieldChange;
import com.das.cleanddd.domain.shared.storage.StorageCondition;

/**
 * {@code changes} lists the fields the edit changed with their previous values, and {@code reason} why, for the
 * audit trail only: they are not published, so it may include fields other services never see.
 */
public record ProductUpdatedEvent(
        String id,
        String name,
        String description,
        BigDecimal price,
        String unit,
        Boolean active,
        String gtin,
        StorageCondition storageCondition,
        List<FieldChange> changes,
        String reason) implements ProductDomainEvent, ChangeTracked {

    public ProductUpdatedEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    /** Without a GTIN. */
    public ProductUpdatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active) {
        this(id, name, description, price, unit, active, null);
    }

    /** Without the changes. */
    public ProductUpdatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active,
                               String gtin) {
        this(id, name, description, price, unit, active, gtin, null, List.of(), null);
    }

    /** Without storage conditions. */
    public ProductUpdatedEvent(String id, String name, String description, BigDecimal price, String unit, Boolean active,
                               String gtin, List<FieldChange> changes, String reason) {
        this(id, name, description, price, unit, active, gtin, null, changes, reason);
    }
}
