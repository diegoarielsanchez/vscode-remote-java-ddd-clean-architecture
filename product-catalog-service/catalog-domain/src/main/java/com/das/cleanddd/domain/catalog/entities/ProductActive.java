package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.shared.BoolValueObject;

public class ProductActive extends BoolValueObject {
    public ProductActive(Boolean value) {
        super(value);
    }

    public ProductActive() {
        super(false);
    }
}
