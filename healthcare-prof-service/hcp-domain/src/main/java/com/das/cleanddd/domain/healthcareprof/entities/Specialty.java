package com.das.cleanddd.domain.healthcareprof.entities;

public record Specialty(String code, String name) {
    public static final int MAX_LENGTH = 100;
    public static final int MIN_LENGTH = 2;
    public static final String REGEX = "^[A-Za-zÀ-ÖØ-öø-ÿ\\s]+$";
    public static final String ERROR_MESSAGE_NULL = "Name cannot be null.";
    public static final String ERROR_MESSAGE_EMPTY = "Name cannot be empty.";
    public static final String ERROR_MESSAGE_INVALID =
        "Name must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters long and contain only letters and spaces.";

    public Specialty {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Code cannot be null or empty.");
        }
        if (name == null) {
            throw new IllegalArgumentException(ERROR_MESSAGE_NULL);
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException(ERROR_MESSAGE_EMPTY);
        }
        if (name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID);
        }
        if (!name.matches(REGEX)) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID);
        }
    }
}
