package com.das.cleanddd.domain.catalog.lot;

import java.time.LocalDate;

/**
 * Units taken from one lot for an order line. Kept with the reservation, so releasing gives them back to the same
 * lot and a recall can list who received a lot. {@code expirationDate} is {@code null} only for migrated stock.
 */
public record LotAllocation(String lotId, String batchNumber, LocalDate expirationDate, int quantity) {}
