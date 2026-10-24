package com.das.cleanddd.domain.catalog.lot;

import java.time.LocalDate;

import com.das.cleanddd.domain.shared.DateValueObject;

/** GS1 expiration date (AI 17). The lot may be used up to and including this day; it is expired from the next. */
public final class ExpirationDate extends DateValueObject {

    public ExpirationDate(LocalDate value) {
        super(value);
        if (value == null) {
            throw new IllegalArgumentException("Expiration date cannot be null.");
        }
    }

    public boolean isExpiredOn(LocalDate today) {
        return today.isAfter(value());
    }
}
