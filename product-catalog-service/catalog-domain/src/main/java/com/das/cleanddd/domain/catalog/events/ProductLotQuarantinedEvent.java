package com.das.cleanddd.domain.catalog.events;

/** A lot was blocked: its units stay on hand but are no longer delivered. */
public record ProductLotQuarantinedEvent(String id, String batchNumber, String reason) implements ProductDomainEvent {
}
