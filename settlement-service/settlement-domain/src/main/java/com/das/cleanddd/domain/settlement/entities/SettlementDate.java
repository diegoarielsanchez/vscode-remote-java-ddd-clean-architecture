package com.das.cleanddd.domain.settlement.entities;

import java.time.LocalDate;

import com.das.cleanddd.domain.shared.DateValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * Value object representing the date a settlement was created.
 */
public final class SettlementDate extends DateValueObject {

    public SettlementDate(LocalDate value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Settlement date is required.");
        }
    }
}
