package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.util.List;

/**
 * The lot after the readings were recorded. {@code withinConditions} is false when any reading broke the product's
 * {@code storageCondition}; each breach is listed. A breach puts an available lot in quarantine
 * ({@code quarantined}) until Quality lifts the quarantine or rejects the lot.
 */
public record ExcursionOutputDTO(
    StockLotOutputDTO lot,
    StorageConditionDTO storageCondition,
    boolean withinConditions,
    List<String> breaches,
    boolean quarantined
) {

    public ExcursionOutputDTO {
        breaches = breaches == null ? List.of() : List.copyOf(breaches);
    }
}
