package com.das.cleanddd.domain.catalog.entities;

import com.das.cleanddd.domain.shared.StringValueObject;

/** Optional serial number, as printed on the pack (GS1 AI 21). */
public class ProductSerialNumber extends StringValueObject {

    public ProductSerialNumber(String value) {
        super(TraceabilityCode.require(value, "Serial number"));
    }
}
