package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.shared.StringValueObject;

/** Optional manufacturing batch (lot) number, as printed on the pack (GS1 AI 10). Needed to trace a recall. */
public class ProductBatchNumber extends StringValueObject {

    public ProductBatchNumber(String value) {
        super(TraceabilityCode.require(value, "Batch number"));
    }
}
