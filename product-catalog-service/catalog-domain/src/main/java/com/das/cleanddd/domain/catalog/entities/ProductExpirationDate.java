package com.das.cleanddd.domain.catalog.entities;

import java.time.LocalDate;

import com.das.cleanddd.domain.shared.DateValueObject;

/**
 * Optional expiration date, as printed on the pack (GS1 AI 17). The product may be used up to and including
 * this day; from the next day it is expired and can no longer be reserved for delivery.
 */
public class ProductExpirationDate extends DateValueObject {

    public ProductExpirationDate(LocalDate value) {
        super(value);
        if (value == null) {
            throw new IllegalArgumentException("Expiration date cannot be null.");
        }
    }

    /** True once {@code today} is after the expiration date. */
    public boolean isExpiredOn(LocalDate today) {
        return today.isAfter(value());
    }
}
