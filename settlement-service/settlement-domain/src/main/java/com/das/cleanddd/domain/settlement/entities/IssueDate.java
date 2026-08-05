package com.das.cleanddd.domain.settlement.entities;

import java.time.LocalDate;

import com.das.cleanddd.domain.shared.DateValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * Value object representing the date an invoice was issued.
 * Business rule: must be within the last 60 days.
 */
public final class IssueDate extends DateValueObject {

    public IssueDate(LocalDate value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Issue date is required.");
        }
        if (value.isBefore(LocalDate.now().minusDays(60))) {
            throw new BusinessValidationException("Issue date must be within the last 60 days.");
        }
    }
}
