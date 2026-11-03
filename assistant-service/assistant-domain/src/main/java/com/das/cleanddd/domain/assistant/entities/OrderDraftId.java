package com.das.cleanddd.domain.assistant.entities;

import com.das.cleanddd.domain.shared.Identifier;

public class OrderDraftId extends Identifier {
    public OrderDraftId(String value) {
        super(value);
    }

    public static OrderDraftId random() {
        return new OrderDraftId(java.util.UUID.randomUUID().toString());
    }
}
