package com.das.cleanddd.domain.shared.storage;

import java.math.BigDecimal;

/**
 * What a data logger or monitoring system recorded over a period, for comparison with a {@link StorageCondition}:
 * the lowest and highest temperature in °C (at most one decimal), the highest relative humidity, and whether the
 * goods were exposed to light. Any of them may be missing, but not all.
 */
public record ConditionReadings(BigDecimal minTemperatureC, BigDecimal maxTemperatureC, Integer maxHumidityPercent,
                                Boolean lightExposed) {

    public static final int LOWEST_C = -100;
    public static final int HIGHEST_C = 100;

    public ConditionReadings {
        if (minTemperatureC == null && maxTemperatureC == null && maxHumidityPercent == null && lightExposed == null) {
            throw new IllegalArgumentException("Give at least one reading: temperature, humidity or light exposure.");
        }
        // Readings are measured, so they may lie outside any label's range, but not outside what a logger reads.
        minTemperatureC = minTemperatureC == null ? null
                : StorageCondition.temperature("minTemperatureC", minTemperatureC, LOWEST_C, HIGHEST_C);
        maxTemperatureC = maxTemperatureC == null ? null
                : StorageCondition.temperature("maxTemperatureC", maxTemperatureC, LOWEST_C, HIGHEST_C);
        if (minTemperatureC != null && maxTemperatureC != null && minTemperatureC.compareTo(maxTemperatureC) > 0) {
            throw new IllegalArgumentException("The lowest reading cannot be above the highest.");
        }
        if (maxHumidityPercent != null && (maxHumidityPercent < 0 || maxHumidityPercent > 100)) {
            throw new IllegalArgumentException("The humidity reading must be between 0 and 100 %.");
        }
    }

    /** E.g. {@code 3.1 to 12.4 °C, 70 % RH, exposed to light}. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        if (minTemperatureC != null || maxTemperatureC != null) {
            text.append(minTemperatureC == null ? "?" : minTemperatureC.toPlainString()).append(" to ")
                    .append(maxTemperatureC == null ? "?" : maxTemperatureC.toPlainString()).append(" °C");
        }
        if (maxHumidityPercent != null) {
            text.append(text.isEmpty() ? "" : ", ").append(maxHumidityPercent).append(" % RH");
        }
        if (lightExposed != null) {
            text.append(text.isEmpty() ? "" : ", ").append(lightExposed ? "exposed to light" : "kept from light");
        }
        return text.toString();
    }
}
