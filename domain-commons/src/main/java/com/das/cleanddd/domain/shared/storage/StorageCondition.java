package com.das.cleanddd.domain.shared.storage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The conditions a product must be stored and transported under, as on its label (GDP: EU 2013/C 343/01 chapters 3
 * and 9; 21 CFR 211.142; for drug samples 21 CFR 203.32). GS1 GDSN carries the same range as the trade item's
 * minimum and maximum storage and handling temperature.
 *
 * <ul>
 *   <li>{@code minTemperatureC} and {@code maxTemperatureC}: required, in °C with at most one decimal, between
 *       {@value #LOWEST_C} and {@value #HIGHEST_C}, minimum not above maximum.</li>
 *   <li>{@code maxHumidityPercent}: relative humidity limit, 1–100; {@code null} when the label sets none.</li>
 *   <li>{@code protectFromLight}: the label says to keep it away from light.</li>
 * </ul>
 */
public record StorageCondition(BigDecimal minTemperatureC, BigDecimal maxTemperatureC, Integer maxHumidityPercent,
                               boolean protectFromLight) {

    public static final int LOWEST_C = -90;
    public static final int HIGHEST_C = 60;
    /** A product that must stay at or below this temperature travels in the cold chain (refrigerated or frozen). */
    public static final BigDecimal COLD_CHAIN_MAX_C = new BigDecimal("8.0");

    public StorageCondition {
        minTemperatureC = temperature("minTemperatureC", minTemperatureC, LOWEST_C, HIGHEST_C);
        maxTemperatureC = temperature("maxTemperatureC", maxTemperatureC, LOWEST_C, HIGHEST_C);
        if (minTemperatureC.compareTo(maxTemperatureC) > 0) {
            throw new IllegalArgumentException("The minimum storage temperature cannot be above the maximum.");
        }
        if (maxHumidityPercent != null && (maxHumidityPercent < 1 || maxHumidityPercent > 100)) {
            throw new IllegalArgumentException("The humidity limit must be between 1 and 100 %.");
        }
    }

    public boolean coldChain() {
        return maxTemperatureC.compareTo(COLD_CHAIN_MAX_C) <= 0;
    }

    /** E.g. {@code 2.0 to 8.0 °C, at most 60 % RH, protect from light}. */
    public String describe() {
        StringBuilder text = new StringBuilder(minTemperatureC.toPlainString()).append(" to ")
                .append(maxTemperatureC.toPlainString()).append(" °C");
        if (maxHumidityPercent != null) {
            text.append(", at most ").append(maxHumidityPercent).append(" % RH");
        }
        if (protectFromLight) {
            text.append(", protect from light");
        }
        return text.toString();
    }

    /**
     * How the readings fall outside these conditions, one sentence per breach; empty when they stay within them.
     * A reading that was not taken is not judged.
     */
    public List<String> breachesOf(ConditionReadings readings) {
        List<String> breaches = new ArrayList<>();
        if (readings.minTemperatureC() != null && readings.minTemperatureC().compareTo(minTemperatureC) < 0) {
            breaches.add("Lowest temperature " + readings.minTemperatureC().toPlainString() + " °C is below "
                    + minTemperatureC.toPlainString() + " °C.");
        }
        if (readings.maxTemperatureC() != null && readings.maxTemperatureC().compareTo(maxTemperatureC) > 0) {
            breaches.add("Highest temperature " + readings.maxTemperatureC().toPlainString() + " °C is above "
                    + maxTemperatureC.toPlainString() + " °C.");
        }
        if (maxHumidityPercent != null && readings.maxHumidityPercent() != null
                && readings.maxHumidityPercent() > maxHumidityPercent) {
            breaches.add("Humidity " + readings.maxHumidityPercent() + " % RH is above " + maxHumidityPercent + " % RH.");
        }
        if (protectFromLight && Boolean.TRUE.equals(readings.lightExposed())) {
            breaches.add("Exposed to light; the product must be protected from light.");
        }
        return List.copyOf(breaches);
    }

    static BigDecimal temperature(String field, BigDecimal value, int lowest, int highest) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (value.stripTrailingZeros().scale() > 1) {
            throw new IllegalArgumentException(field + " can have at most one decimal.");
        }
        if (value.compareTo(BigDecimal.valueOf(lowest)) < 0 || value.compareTo(BigDecimal.valueOf(highest)) > 0) {
            throw new IllegalArgumentException(field + " must be between " + lowest + " and " + highest + " °C.");
        }
        return value.setScale(1, RoundingMode.UNNECESSARY);
    }
}
