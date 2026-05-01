package com.das.cleanddd.domain.medicalsalesrep.events;

public record MsrActivatedEvent(
        String id,
        String name,
        String surname,
        String email,
        Boolean active) implements MsrDomainEvent {
}
