package com.das.cleanddd.domain.settlement.entities;

import java.time.LocalDate;

import com.das.cleanddd.domain.shared.DateValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * Value object representing the date an invoice was issued.
 * Business rule: must be at least 60 days in the past.
 */
public final class IssueDate extends DateValueObject {

    public IssueDate(LocalDate value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Issue date is required.");
        }
        if (value.isAfter(LocalDate.now().minusDays(60))) {
            throw new BusinessValidationException("Issue date must be at least 60 days in the past.");
        }
    }
}
