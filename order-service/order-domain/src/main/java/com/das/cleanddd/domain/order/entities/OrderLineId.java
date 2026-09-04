package com.das.cleanddd.domain.order.entities;

import com.das.cleanddd.domain.shared.Identifier;

public class OrderLineId extends Identifier {
    public OrderLineId(String value) {
        super(value);
    }

    public static OrderLineId random() {
        return new OrderLineId(java.util.UUID.randomUUID().toString());
    }
}
