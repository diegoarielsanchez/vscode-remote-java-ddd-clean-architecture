package com.das.cleanddd.domain.healthcareprof.events;

public record HcpDeactivatedEvent(
        String id,
        Boolean active
) implements HcpDomainEvent {
}
