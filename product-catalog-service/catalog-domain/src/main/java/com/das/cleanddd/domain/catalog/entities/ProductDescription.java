package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.shared.TextValueObject;

/** Optional: a product may have no description on file yet. */
public class ProductDescription extends TextValueObject {
    public ProductDescription(String value) {
        super(value);
    }
}
