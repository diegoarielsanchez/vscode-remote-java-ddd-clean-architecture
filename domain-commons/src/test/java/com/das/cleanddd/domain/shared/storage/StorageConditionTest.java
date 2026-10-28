package com.das.cleanddd.domain.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class StorageConditionTest {

    private static final StorageCondition FRIDGE = new StorageCondition(new BigDecimal("2"), new BigDecimal("8"), 60, true);

    @Test
    void describesTheLabelAndKnowsTheColdChain() {
        assertEquals("2.0 to 8.0 °C, at most 60 % RH, protect from light", FRIDGE.describe());
        assertTrue(FRIDGE.coldChain());
        assertFalse(new StorageCondition(new BigDecimal("15"), new BigDecimal("25"), null, false).coldChain());
    }

    @Test
    void refusesConditionsThatCannotBeOnALabel() {
        assertThrows(IllegalArgumentException.class, () -> new StorageCondition(null, BigDecimal.TEN, null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new StorageCondition(new BigDecimal("8"), new BigDecimal("2"), null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new StorageCondition(new BigDecimal("2.05"), new BigDecimal("8"), null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new StorageCondition(new BigDecimal("-100"), new BigDecimal("8"), null, false));
        assertThrows(IllegalArgumentException.class,
                () -> new StorageCondition(new BigDecimal("2"), new BigDecimal("8"), 0, false));
    }

    @Test
    void listsEachBreachOfTheReadings() {
        List<String> breaches = FRIDGE.breachesOf(new ConditionReadings(new BigDecimal("1.5"), new BigDecimal("12.4"), 75, true));

        assertEquals(List.of(
                "Lowest temperature 1.5 °C is below 2.0 °C.",
                "Highest temperature 12.4 °C is above 8.0 °C.",
                "Humidity 75 % RH is above 60 % RH.",
                "Exposed to light; the product must be protected from light."), breaches);
    }

    @Test
    void readingsWithinTheConditionsOrNotTakenAreNoBreach() {
        assertTrue(FRIDGE.breachesOf(new ConditionReadings(new BigDecimal("2"), new BigDecimal("8"), 60, false)).isEmpty());
        assertTrue(FRIDGE.breachesOf(new ConditionReadings(null, new BigDecimal("7.9"), null, null)).isEmpty());
    }

    @Test
    void readingsNeedAtLeastOneValue() {
        assertThrows(IllegalArgumentException.class, () -> new ConditionReadings(null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ConditionReadings(new BigDecimal("9"), new BigDecimal("3"), null, null));
        assertEquals("3.0 to 12.4 °C, exposed to light",
                new ConditionReadings(new BigDecimal("3"), new BigDecimal("12.4"), null, true).describe());
    }
}
