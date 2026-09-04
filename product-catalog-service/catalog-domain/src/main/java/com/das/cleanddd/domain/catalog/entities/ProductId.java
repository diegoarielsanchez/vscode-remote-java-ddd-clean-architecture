package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.shared.Identifier;

public class ProductId extends Identifier {
    public ProductId(String value) {
        super(value);
    }

    public static ProductId random() {
        return new ProductId(java.util.UUID.randomUUID().toString());
    }
}
