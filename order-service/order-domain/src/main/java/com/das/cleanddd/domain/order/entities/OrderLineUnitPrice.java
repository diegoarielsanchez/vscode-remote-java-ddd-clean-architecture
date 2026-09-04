package com.das.cleanddd.domain.order.entities;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.AmountValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * A price SNAPSHOT captured at reservation time — deliberately not re-fetched
 * from product-catalog on every read, so a later catalog price change never
 * silently reprices a historical order.
 */
public final class OrderLineUnitPrice extends AmountValueObject {

    public OrderLineUnitPrice(BigDecimal value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Unit price is required.");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("Unit price must be zero or positive.");
        }
    }
}
