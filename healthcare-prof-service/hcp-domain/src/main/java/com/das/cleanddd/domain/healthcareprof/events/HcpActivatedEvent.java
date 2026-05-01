package com.das.cleanddd.domain.healthcareprof.events;

public record HcpActivatedEvent(
        String id,
        Boolean active
) implements HcpDomainEvent {
}
