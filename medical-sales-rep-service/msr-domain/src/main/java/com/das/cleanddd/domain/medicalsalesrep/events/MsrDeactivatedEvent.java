package com.das.cleanddd.domain.medicalsalesrep.events;

public record MsrDeactivatedEvent(
        String id,
        String name,
        String surname,
        String email,
        Boolean active) implements MsrDomainEvent {
}
