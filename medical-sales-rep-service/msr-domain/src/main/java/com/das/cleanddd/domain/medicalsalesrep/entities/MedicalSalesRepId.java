package com.das.cleanddd.domain.medicalsalesrep.entities;

import com.das.cleanddd.domain.shared.Identifier;

public class MedicalSalesRepId extends Identifier{
    public MedicalSalesRepId(String value) {
        super(value);
    }

    public static MedicalSalesRepId random() {
        return new MedicalSalesRepId(java.util.UUID.randomUUID().toString());
    }
}
