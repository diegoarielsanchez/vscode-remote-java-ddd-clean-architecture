package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.shared.StringValueObject;

public class ProductName extends StringValueObject {
    public static final int MAX_LENGTH = 200;
    public static final int MIN_LENGTH = 2;
    public static final String ERROR_MESSAGE_NULL = "Name cannot be null.";
    public static final String ERROR_MESSAGE_EMPTY = "Name cannot be empty.";
    public static final String ERROR_MESSAGE_INVALID =
            "Name must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters long.";

    public ProductName(String value) {
        super(value);
        if (value == null) {
            throw new IllegalArgumentException(ERROR_MESSAGE_NULL);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException(ERROR_MESSAGE_EMPTY);
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(ERROR_MESSAGE_INVALID);
        }
    }
}
