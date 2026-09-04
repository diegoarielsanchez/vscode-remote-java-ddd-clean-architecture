package com.das.cleanddd.domain.catalog.entities;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.AmountValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * Value object representing a product's unit price.
 * Business rule: must be zero or positive.
 */
public final class ProductPrice extends AmountValueObject {

    public ProductPrice(BigDecimal value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Price is required.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("Price must be zero or positive.");
        }
    }
}
