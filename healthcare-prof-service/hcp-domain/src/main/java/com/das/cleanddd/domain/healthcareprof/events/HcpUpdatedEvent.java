package com.das.cleanddd.domain.healthcareprof.events;

public record HcpUpdatedEvent(
        String id,
        String name,
        String surname,
        String email,
        Boolean active
) implements HcpDomainEvent {
}
