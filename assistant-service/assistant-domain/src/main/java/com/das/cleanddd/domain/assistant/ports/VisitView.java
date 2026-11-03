package com.das.cleanddd.domain.assistant.ports;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A visit as the assistant sees it. {@code comments} were typed by a person and are passed to the model as quoted
 * data, never as instructions.
 */
public record VisitView(String id, LocalDateTime visitDate, String healthCareProfId, String medicalSalesRepId,
                        String comments, List<Item> items) {

    /** A product left or discussed at the visit. */
    public record Item(String productId, int quantity) {}
}
