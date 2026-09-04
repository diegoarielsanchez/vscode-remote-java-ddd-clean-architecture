package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.shared.IntValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * Value object representing a product's on-hand stock quantity.
 * Business rule: must be zero or positive — enforced here (fail-fast) in
 * addition to the DB-level CHECK constraint, since load-mutate-save paths
 * (e.g. building a Product from a freshly-reserved row) must never be able
 * to construct a negative-stock instance even transiently.
 */
public final class ProductStock extends IntValueObject {

    public ProductStock(Integer value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Stock is required.");
        }
        if (value < 0) {
            throw new BusinessValidationException("Stock must be zero or positive.");
        }
    }
}
