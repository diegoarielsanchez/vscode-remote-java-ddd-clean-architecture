package com.das.cleanddd.domain.order.entities;

import com.das.cleanddd.domain.shared.Identifier;

public class OrderId extends Identifier {
    public OrderId(String value) {
        super(value);
    }

    public static OrderId random() {
        return new OrderId(java.util.UUID.randomUUID().toString());
    }
}
