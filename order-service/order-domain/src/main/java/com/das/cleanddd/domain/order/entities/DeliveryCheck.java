package com.das.cleanddd.domain.order.entities;

import java.util.List;

import com.das.cleanddd.domain.shared.storage.ConditionReadings;

/**
 * What the receiver reported about transport when confirming delivery (GDP: the recipient checks that the
 * conditions were kept, and quarantines the goods when they were not).
 *
 * <ul>
 *   <li>{@code conditionsMaintained}: the receiver's statement.</li>
 *   <li>{@code readings}: the shipment's logger readings, when there is one; {@code null} otherwise.</li>
 *   <li>{@code note}: optional, at most {@value #MAX_NOTE} characters.</li>
 *   <li>{@code breaches}: how the readings broke the lines' storage conditions, worked out by the order.</li>
 * </ul>
 */
public record DeliveryCheck(boolean conditionsMaintained, ConditionReadings readings, String note,
                            List<String> breaches) {

    public static final int MAX_NOTE = 500;

    public DeliveryCheck {
        note = note == null || note.isBlank() ? null : note.strip();
        if (note != null && note.length() > MAX_NOTE) {
            throw new IllegalArgumentException("The transport note must be at most " + MAX_NOTE + " characters.");
        }
        breaches = breaches == null ? List.of() : List.copyOf(breaches);
    }

    /** As reported, before it is compared with the order's lines. */
    public static DeliveryCheck reported(boolean conditionsMaintained, ConditionReadings readings, String note) {
        return new DeliveryCheck(conditionsMaintained, readings, note, List.of());
    }

    DeliveryCheck withBreaches(List<String> found) {
        return new DeliveryCheck(conditionsMaintained, readings, note, found);
    }

    /** The goods may be used: the receiver says the conditions were kept and no reading says otherwise. */
    public boolean kept() {
        return conditionsMaintained && breaches.isEmpty();
    }
}
