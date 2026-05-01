package com.das.cleanddd.domain.healthcareprof.events;

public record HcpCreatedEvent(
        String id,
        String name,
        String surname,
        String email,
        Boolean active
) implements HcpDomainEvent {
}
