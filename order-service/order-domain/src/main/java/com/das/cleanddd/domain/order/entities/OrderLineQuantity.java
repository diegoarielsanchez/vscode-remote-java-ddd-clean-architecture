package com.das.cleanddd.domain.order.entities;

import com.das.cleanddd.domain.shared.IntValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

public final class OrderLineQuantity extends IntValueObject {

    public OrderLineQuantity(Integer value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Quantity is required.");
        }
        if (value <= 0) {
            throw new BusinessValidationException("Quantity must be greater than zero.");
        }
    }
}
