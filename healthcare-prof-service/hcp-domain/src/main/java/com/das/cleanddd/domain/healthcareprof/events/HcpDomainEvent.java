package com.das.cleanddd.domain.healthcareprof.events;

public sealed interface HcpDomainEvent
        permits HcpCreatedEvent, HcpUpdatedEvent, HcpActivatedEvent, HcpDeactivatedEvent {
}
