package com.das.cleanddd.domain.shared;

public abstract class EmailValueObject extends StringValueObject {
    public static final String REGEX = "^[a-zA-Z0-9_!#$%&’*+/=?`{|}~^-]+(?:\\.[a-zA-Z0-9_!#$%&’*+/=?`{|}~^-]+)*@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$";
    public static final String ERROR_MESSAGE_NULL = "Email cannot be null.";
    public static final String ERROR_MESSAGE_EMPTY = "Email cannot be empty.";
    public static final String ERROR_MESSAGE_INVALID = "Email is not valid.";
    public static final String ERROR_MESSAGE_INVALID_FORMAT = "Email format is not valid.";
    public static final String ERROR_MESSAGE_INVALID_LENGTH = "Email length is not valid.";
    public static final int MAX_LENGTH = 100;
    public static final int MIN_LENGTH = 5;
    public static final String ERROR_MESSAGE = "Email must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters long and contain only letters, numbers, and special characters.";
    public static final String ERROR_MESSAGE_INVALID_CHARACTERS = "Email contains invalid characters.";

    protected EmailValueObject(String value) {
        this(value, false);
    }

    protected EmailValueObject(String value, boolean allowNull) {
        super(value);
        validate(value, allowNull);
    }

    private static void validate(String value, boolean allowNull) {
        if (value == null) {
            if (allowNull) {
                return;
            }
            throw new IllegalArgumentException(ERROR_MESSAGE_NULL);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(ERROR_MESSAGE_EMPTY);
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID_LENGTH);
        }
        if (!value.matches(REGEX)) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID_FORMAT);
        }
    }
}