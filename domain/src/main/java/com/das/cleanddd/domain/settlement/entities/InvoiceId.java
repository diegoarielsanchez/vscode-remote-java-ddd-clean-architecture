package com.das.cleanddd.domain.settlement.entities;

import com.das.cleanddd.domain.shared.Identifier;

public final class InvoiceId extends Identifier {

    public InvoiceId(String value) {
        super(value);
    }

    public static InvoiceId random() {
        return new InvoiceId(java.util.UUID.randomUUID().toString());
    }
}
