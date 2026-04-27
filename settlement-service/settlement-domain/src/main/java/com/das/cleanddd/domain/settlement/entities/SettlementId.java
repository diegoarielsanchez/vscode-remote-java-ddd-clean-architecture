package com.das.cleanddd.domain.settlement.entities;

import com.das.cleanddd.domain.shared.Identifier;

public final class SettlementId extends Identifier {

    public SettlementId(String value) {
        super(value);
    }

    public static SettlementId random() {
        return new SettlementId(java.util.UUID.randomUUID().toString());
    }
}
