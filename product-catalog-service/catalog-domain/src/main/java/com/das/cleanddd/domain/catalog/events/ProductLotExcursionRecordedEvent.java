package com.das.cleanddd.domain.catalog.events;

import java.time.Instant;
import java.util.List;

import com.das.cleanddd.domain.shared.storage.ConditionReadings;

/**
 * Readings for a lot over a period were recorded and compared with the product's storage conditions (GDP: every
 * excursion is recorded and investigated). {@code breaches} is empty when the readings stayed within them;
 * {@code quarantined} is true when this record put the lot in quarantine.
 */
public record ProductLotExcursionRecordedEvent(String id, String batchNumber, String source, Instant observedFrom,
                                               Instant observedTo, ConditionReadings readings, List<String> breaches,
                                               boolean quarantined, String note) implements ProductDomainEvent {

    public ProductLotExcursionRecordedEvent {
        breaches = breaches == null ? List.of() : List.copyOf(breaches);
    }
}
