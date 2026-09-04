package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.shared.StringValueObject;

/** Free-text unit of measure ("UNIT", "BOX", "VIAL", ...) — no controlled vocabulary yet. */
public class ProductUnit extends StringValueObject {
    public static final String ERROR_MESSAGE_NULL = "Unit cannot be null.";
    public static final String ERROR_MESSAGE_EMPTY = "Unit cannot be empty.";

    public ProductUnit(String value) {
        super(value);
        if (value == null) {
            throw new IllegalArgumentException(ERROR_MESSAGE_NULL);
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(ERROR_MESSAGE_EMPTY);
        }
    }
}
