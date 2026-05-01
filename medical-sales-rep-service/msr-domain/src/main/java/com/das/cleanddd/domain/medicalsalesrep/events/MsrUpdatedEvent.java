package com.das.cleanddd.domain.medicalsalesrep.events;

public record MsrUpdatedEvent(
        String id,
        String name,
        String surname,
        String email,
        Boolean active) implements MsrDomainEvent {
}
