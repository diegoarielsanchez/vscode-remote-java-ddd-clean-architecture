package com.das.cleanddd.domain.medicalsalesrep.events;

public record MsrCreatedEvent(
        String id,
        String name,
        String surname,
        String email,
        Boolean active) implements MsrDomainEvent {
}
