package com.das.cleanddd.domain.catalog.events;

/** A quarantined lot may be delivered again. */
public record ProductLotQuarantineLiftedEvent(String id, String batchNumber, String reason) implements ProductDomainEvent {
}
