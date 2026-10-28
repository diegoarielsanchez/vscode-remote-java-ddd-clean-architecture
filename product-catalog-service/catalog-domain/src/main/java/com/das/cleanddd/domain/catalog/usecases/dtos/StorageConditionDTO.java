package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.storage.StorageCondition;

/**
 * A product's storage and transport conditions, from its label: temperature range in °C (at most one decimal),
 * optional relative humidity limit in %, and whether it must be protected from light. Out: {@code coldChain} is
 * true when the product must stay at or below 8 °C, and {@code description} reads e.g. {@code 2.0 to 8.0 °C};
 * both are ignored in.
 */
public record StorageConditionDTO(
    BigDecimal minTemperatureC,
    BigDecimal maxTemperatureC,
    Integer maxHumidityPercent,
    Boolean protectFromLight,
    Boolean coldChain,
    String description
) {

    public StorageConditionDTO(BigDecimal minTemperatureC, BigDecimal maxTemperatureC, Integer maxHumidityPercent,
                               Boolean protectFromLight) {
        this(minTemperatureC, maxTemperatureC, maxHumidityPercent, protectFromLight, null, null);
    }

    /** @throws IllegalArgumentException when the conditions are not valid */
    public StorageCondition toDomain() {
        return new StorageCondition(minTemperatureC, maxTemperatureC, maxHumidityPercent,
                Boolean.TRUE.equals(protectFromLight));
    }

    public static StorageConditionDTO from(StorageCondition condition) {
        return condition == null ? null : new StorageConditionDTO(condition.minTemperatureC(),
                condition.maxTemperatureC(), condition.maxHumidityPercent(), condition.protectFromLight(),
                condition.coldChain(), condition.describe());
    }
}
