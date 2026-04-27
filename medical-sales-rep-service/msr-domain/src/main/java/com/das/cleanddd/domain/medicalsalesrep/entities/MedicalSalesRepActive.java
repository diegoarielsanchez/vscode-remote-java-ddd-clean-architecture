package com.das.cleanddd.domain.medicalsalesrep.entities;

import com.das.cleanddd.domain.shared.BoolValueObject;

public class MedicalSalesRepActive extends BoolValueObject {
    public MedicalSalesRepActive(Boolean value) {
        super(value);
    }

    public MedicalSalesRepActive() {
        super(false);
    }
}
