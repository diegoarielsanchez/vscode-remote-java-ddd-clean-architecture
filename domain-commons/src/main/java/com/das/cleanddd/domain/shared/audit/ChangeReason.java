package com.das.cleanddd.domain.shared.audit;

/**
 * Why an existing record was changed, as entered by the person changing it (ALCOA+: a GxP audit trail records the
 * reason for every change to existing data). Required, at most {@value #MAX_LENGTH} characters, kept exactly as
 * entered apart from surrounding blanks.
 */
public record ChangeReason(String value) {

    public static final int MAX_LENGTH = 500;

    public ChangeReason {
        value = value == null ? null : value.strip();
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("A reason for the change is required.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The reason for the change must be at most " + MAX_LENGTH + " characters.");
        }
    }

    /** {@code null} when no reason is given; for events that may carry one. */
    public static String valueOf(ChangeReason reason) {
        return reason == null ? null : reason.value();
    }
}
