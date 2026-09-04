package com.das.cleanddd.domain.order.entities;

import com.das.cleanddd.domain.shared.Identifier;

/** Local copy of the product-catalog identity — see {@link MedicalSalesRepId} for the ACL rationale. */
public class ProductId extends Identifier {
    public ProductId(String value) {
        super(value);
    }
}
