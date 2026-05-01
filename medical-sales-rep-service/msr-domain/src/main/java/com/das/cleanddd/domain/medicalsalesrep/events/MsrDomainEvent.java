package com.das.cleanddd.domain.medicalsalesrep.events;

public sealed interface MsrDomainEvent
        permits MsrCreatedEvent, MsrUpdatedEvent, MsrActivatedEvent, MsrDeactivatedEvent {
}
