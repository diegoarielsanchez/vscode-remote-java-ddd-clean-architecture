package com.das.cleanddd.domain.settlement.entities;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.AmountValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * Value object representing an invoice's monetary amount.
 * Business rule: must be zero or positive.
 */
public final class InvoiceAmount extends AmountValueObject {

    public InvoiceAmount(BigDecimal value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Amount is required.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("Amount must be zero or positive.");
        }
    }
}
