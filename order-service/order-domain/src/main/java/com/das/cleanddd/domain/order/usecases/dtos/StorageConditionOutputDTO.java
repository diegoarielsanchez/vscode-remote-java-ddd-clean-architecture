package com.das.cleanddd.domain.order.usecases.dtos;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.storage.StorageCondition;

/** The conditions a line's units must travel under; {@code description} reads e.g. {@code 2.0 to 8.0 °C}. */
public record StorageConditionOutputDTO(
    BigDecimal minTemperatureC,
    BigDecimal maxTemperatureC,
    Integer maxHumidityPercent,
    boolean protectFromLight,
    boolean coldChain,
    String description
) {

    public static StorageConditionOutputDTO from(StorageCondition condition) {
        return condition == null ? null : new StorageConditionOutputDTO(condition.minTemperatureC(),
                condition.maxTemperatureC(), condition.maxHumidityPercent(), condition.protectFromLight(),
                condition.coldChain(), condition.describe());
    }
}
