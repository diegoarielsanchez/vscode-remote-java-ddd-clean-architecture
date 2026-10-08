package com.das.infra.service.medicalsalesrep.outbox;

import com.das.cleanddd.domain.medicalsalesrep.events.MsrActivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrCreatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDeactivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDomainEvent;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrUpdatedEvent;

/**
 * Maps private domain events to the public {@code msr.events} contract (schema version 1).
 *
 * <p>Data minimization: the payload carries only what consumers use (name, surname, active).
 * The e-mail address stays inside this bounded context.
 */
public final class MsrIntegrationEvents {

    public static final String AGGREGATE_TYPE = "msr";
    public static final int SCHEMA_VERSION = 1;

    /** Event payload ({@code data}) for every {@code msr.*} event; null fields are "unchanged / not applicable". */
    public record MsrEventData(String name, String surname, Boolean active) {}

    public record Mapped(String eventType, String aggregateId, MsrEventData data) {}

    private MsrIntegrationEvents() {}

    public static Mapped map(MsrDomainEvent event) {
        return switch (event) {
            case MsrCreatedEvent e -> new Mapped("msr.created", e.id(), new MsrEventData(e.name(), e.surname(), e.active()));
            case MsrUpdatedEvent e -> new Mapped("msr.updated", e.id(), new MsrEventData(e.name(), e.surname(), e.active()));
            case MsrActivatedEvent e -> new Mapped("msr.activated", e.id(), new MsrEventData(null, null, Boolean.TRUE));
            case MsrDeactivatedEvent e -> new Mapped("msr.deactivated", e.id(), new MsrEventData(null, null, Boolean.FALSE));
        };
    }
}
