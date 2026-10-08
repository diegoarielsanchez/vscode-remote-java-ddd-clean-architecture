package com.das.infra.service.healthcareprof.outbox;

import com.das.cleanddd.domain.healthcareprof.events.HcpActivatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpCreatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpDeactivatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpDomainEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpUpdatedEvent;

/**
 * Maps private domain events to the public {@code hcp.events} contract (schema version 1).
 *
 * <p>Data minimization: the payload carries only what consumers use (name, surname, active).
 * The e-mail address stays inside this bounded context.
 */
public final class HcpIntegrationEvents {

    public static final String AGGREGATE_TYPE = "hcp";
    public static final int SCHEMA_VERSION = 1;

    /** Event payload ({@code data}) for every {@code hcp.*} event; null fields are "unchanged / not applicable". */
    public record HcpEventData(String name, String surname, Boolean active) {}

    public record Mapped(String eventType, String aggregateId, HcpEventData data) {}

    private HcpIntegrationEvents() {}

    public static Mapped map(HcpDomainEvent event) {
        return switch (event) {
            case HcpCreatedEvent e -> new Mapped("hcp.created", e.id(), new HcpEventData(e.name(), e.surname(), e.active()));
            case HcpUpdatedEvent e -> new Mapped("hcp.updated", e.id(), new HcpEventData(e.name(), e.surname(), e.active()));
            case HcpActivatedEvent e -> new Mapped("hcp.activated", e.id(), new HcpEventData(null, null, Boolean.TRUE));
            case HcpDeactivatedEvent e -> new Mapped("hcp.deactivated", e.id(), new HcpEventData(null, null, Boolean.FALSE));
        };
    }
}
